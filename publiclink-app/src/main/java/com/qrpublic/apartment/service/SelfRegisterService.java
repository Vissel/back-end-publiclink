package com.qrpublic.apartment.service;

import com.qrpublic.apartment.adapter.authentication.request.FindUserRequest;
import com.qrpublic.apartment.adapter.template.Result;
import com.qrpublic.apartment.adapter.user.request.ProfileLink;
import com.qrpublic.apartment.adapter.user.request.UserRegisterRequest;
import com.qrpublic.apartment.adapter.user.request.UserRemoveRequest;
import com.qrpublic.apartment.adapter.user.response.UserRegisterResponse;
import com.qrpublic.apartment.core.service.CoreUserService;
import com.qrpublic.apartment.integration.OperatedUserClient;
import com.qrpublic.apartment.model.PlanGroup;
import com.qrpublic.apartment.user.request.SelfRegisterRequest;
import com.qrpublic.apartment.user.response.SelfRegisterResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Open self-registration: a visitor creates their own seller account without
 * an admin invitation. Two systems have to agree on the new account, so the
 * flow is create remote, then write local, then compensate the remote row if
 * the local write fails - the same shape as the invite register flow, minus
 * the request uuid and the update branch.
 *
 * <p>
 * Nothing blocks here: the account service is called reactively and the
 * local JPA write is pushed onto the elastic scheduler.
 */
@Slf4j
@Service
public class SelfRegisterService {

    /**
     * Username is the login id and part of the transfer content; keep it bank-safe.
     */
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{3,30}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");
    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_EMAIL_LENGTH = 100;

    private static final Duration REMOTE_TIMEOUT = Duration.ofSeconds(5);

    private static final String TAKEN_MESSAGE = "This username is already taken. Please choose another one.";
    private static final String SUCCESS_MESSAGE = "Account created. Sign in to continue.";
    private static final String ROLE_BUYER = "BUYER";
    private static final String ROLE_SELLER = "SELLER";

    @Autowired
    private CoreUserService coreUserService;

    @Autowired
    private OperatedUserClient operatedUserClient;

    public Mono<ResponseEntity<SelfRegisterResponse>> selfRegister(SelfRegisterRequest request) {
        String username = trimOf(request.getUsername());
        String name = trimOf(request.getName());
        String email = trimOf(request.getEmail());

        String problem = firstProblem(username, name, email, request.getPassword());
        if (problem != null) {
            return Mono.just(response(HttpStatus.BAD_REQUEST, username, name, email, problem));
        }

        String role = ROLE_BUYER.equalsIgnoreCase(trimOf(request.getRole())) ? ROLE_BUYER : ROLE_SELLER;
        // Asked on the register screen and stored on the local row only: which
        // plan group an account may buy from is a business fact, not part of the
        // identity the account service holds.
        String planPurpose = PlanGroup.fromPurpose(request.getPlanPurpose()).name();
        return usernameTakenLocally(username)
                .flatMap(taken -> taken
                        ? Mono.just(response(HttpStatus.CONFLICT, username, name, email, TAKEN_MESSAGE))
                        : registerRemoteThenLocal(username, name, email, request.getPassword(), role, planPurpose))
                .onErrorResume(ex -> {
                    log.error("Self-registration failed for [{}]", username, ex);
                    return Mono.just(response(HttpStatus.SERVICE_UNAVAILABLE, username, name, email,
                            "The account service is unreachable right now. Please try again in a moment."));
                });
    }

    /**
     * A row in publiclink-db means the name is used even when the account
     * service says nothing, so the local check runs first.
     */
    private Mono<Boolean> usernameTakenLocally(String username) {
        return Mono.fromCallable(() -> coreUserService.findUserEntityByUsername(username).isPresent())
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<ResponseEntity<SelfRegisterResponse>> registerRemoteThenLocal(String username, String name,
            String email, String encryptedPassword, String role, String planPurpose) {
        return operatedUserClient.findUserByUsername(new FindUserRequest(username))
                .timeout(REMOTE_TIMEOUT)
                // any answer means the account service already knows the name
                .flatMap(found -> Mono.just(
                        response(HttpStatus.CONFLICT, username, name, email, TAKEN_MESSAGE)))
                .switchIfEmpty(Mono.defer(
                        () -> createRemoteAccount(username, name, email, encryptedPassword, role, planPurpose)))
                // the account service answers 404 for an unknown name: that is
                // the normal path, not a failure
                .onErrorResume(WebClientResponseException.NotFound.class,
                        ex -> createRemoteAccount(username, name, email, encryptedPassword, role, planPurpose));
    }

    private Mono<ResponseEntity<SelfRegisterResponse>> createRemoteAccount(String username, String name, String email,
            String encryptedPassword, String role, String planPurpose) {
        return operatedUserClient.createUser(toCreateRequest(username, name, encryptedPassword, role))
                .timeout(REMOTE_TIMEOUT)
                .flatMap(result -> {
                    if (result == null || !result.isSuccess()) {
                        return Mono.just(response(HttpStatus.BAD_REQUEST, username, name, email,
                                accountServiceRefusal(result)));
                    }
                    return writeLocalAccount(username, name, email, role, planPurpose);
                })
                .onErrorResume(WebClientResponseException.BadRequest.class,
                        ex -> Mono.just(response(HttpStatus.BAD_REQUEST, username, name, email,
                                "The account was refused. Please check the password and try again.")))
                .onErrorResume(WebClientResponseException.Conflict.class,
                        ex -> Mono.just(response(HttpStatus.CONFLICT, username, name, email, TAKEN_MESSAGE)))
                .onErrorResume(WebClientResponseException.class,
                        ex -> Mono.just(response(HttpStatus.SERVICE_UNAVAILABLE, username, name, email,
                                "The account service is unreachable right now. Please try again in a moment.")));
    }

    private Mono<ResponseEntity<SelfRegisterResponse>> writeLocalAccount(String username, String name, String email,
            String role, String planPurpose) {
        return Mono
                .fromCallable(() -> coreUserService.createSelfRegistered(username, name, email, role, planPurpose))
                .subscribeOn(Schedulers.boundedElastic())
                .map(created -> response(HttpStatus.OK, username, name, email, SUCCESS_MESSAGE))
                .onErrorResume(ex -> {
                    log.error("Local row failed for self-registered [{}], rolling back remote account", username, ex);
                    return deleteRemoteAccount(username)
                            .then(Mono.just(response(HttpStatus.SERVICE_UNAVAILABLE, username, name, email,
                                    "Something went wrong while creating the account. Please try again.")));
                });
    }

    /**
     * Best effort: a failed compensation is logged, the visitor still gets a
     * retry-able error.
     */
    private Mono<Void> deleteRemoteAccount(String username) {
        UserRemoveRequest remove = new UserRemoveRequest();
        remove.setUserId(username);
        remove.setUserName(username);
        return operatedUserClient.deleteUser(remove)
                .doOnSuccess(ignored -> log.info("Compensation deleted remote account [{}]", username))
                .onErrorResume(compEx -> {
                    log.error("Compensation failed for [{}]: {}", username, compEx.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    private UserRegisterRequest toCreateRequest(String username, String name, String encryptedPassword,
            String role) {
        ProfileLink profileLink = new ProfileLink();
        profileLink.setLink("");
        UserRegisterRequest req = new UserRegisterRequest();
        req.setUserName(username);
        req.setFullName(name);
        req.setRole(role);
        req.setEncryptedPassword(encryptedPassword);
        // The account service reads the profile link list off the request and
        // rejects a missing one, so the link object is always present.
        req.setProfileLink(profileLink);
        return req;
    }

    private String accountServiceRefusal(Result<UserRegisterResponse> result) {
        if (result != null && result.getErrorMessage() != null && !result.getErrorMessage().isBlank()) {
            return result.getErrorMessage();
        }
        return "The account was refused. Please check the password and try again.";
    }

    /**
     * The password itself is opaque here - it is decrypted by the account
     * service - so its length rule is enforced by the browser and by that
     * service. Everything else is checked before a single remote call.
     */
    private String firstProblem(String username, String name, String email, String encryptedPassword) {
        if (username.isBlank()) {
            return "Username is required.";
        }
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            return "Username must be 3-30 characters: letters, numbers, dot, dash or underscore.";
        }
        if (name.isBlank()) {
            return "Your name is required.";
        }
        if (name.length() > MAX_NAME_LENGTH) {
            return "Name must be at most 100 characters.";
        }
        if (email.isBlank()) {
            return "An email address is required so we can contact you about your plan and payment.";
        }
        if (email.length() > MAX_EMAIL_LENGTH || !EMAIL_PATTERN.matcher(email).matches()) {
            return "Please enter a valid email address.";
        }
        if (encryptedPassword == null || encryptedPassword.isBlank()) {
            return "A password is required.";
        }
        return null;
    }

    private ResponseEntity<SelfRegisterResponse> response(HttpStatus status, String username, String name,
            String email, String message) {
        SelfRegisterResponse body = new SelfRegisterResponse();
        body.setUsername(username);
        body.setName(name);
        body.setEmail(email);
        body.setMessage(message);
        return ResponseEntity.status(status).<SelfRegisterResponse>body(body);
    }

    private String trimOf(String value) {
        return value == null ? "" : value.trim();
    }
}
