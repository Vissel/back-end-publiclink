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
import java.util.Optional;
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
    private static final String EMAIL_TAKEN_MESSAGE = "This email address is already registered. Sign in with it, or register with another email.";
    private static final String NAME_TAKEN_MESSAGE = "An account with this full name already exists. Please make it distinguishable - add a middle name or a number - and try again.";
    private static final String SUCCESS_MESSAGE = "Account created. Sign in to continue.";
    private static final String ROLE_BUYER = "BUYER";
    private static final String ROLE_SELLER = "SELLER";

    /** The form inputs the response can point at, in the order they are checked. */
    private static final String FIELD_USERNAME = "username";
    private static final String FIELD_EMAIL = "email";
    private static final String FIELD_NAME = "name";

    /**
     * Which stored value the new account would duplicate, with the answer the
     * register screen shows for it.
     */
    private record DuplicateAccount(String field, String message) {
    }

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

        // Name and email are optional on the register screen. The username
        // stands in as the display name, so the admin lists, notification text
        // and transfer copy never show an empty person; the email is simply
        // absent until the account gives one.
        String displayName = name.isBlank() ? username : name;

        String role = ROLE_BUYER.equalsIgnoreCase(trimOf(request.getRole())) ? ROLE_BUYER : ROLE_SELLER;
        // The purpose question moved from the register form to the plan screen
        // (step 2). Registration stores only what it was given - players give
        // none - and leaves the rest unset, so the plan screen can tell "never
        // chose" apart from "chose PRODUCT".
        String purposeRaw = trimOf(request.getPlanPurpose());
        String planPurpose = purposeRaw.isBlank() ? null : PlanGroup.fromPurpose(purposeRaw).name();
        return firstDuplicate(username, email, name)
                .flatMap(duplicate -> duplicate
                        .map(found -> Mono.just(response(HttpStatus.CONFLICT, username, displayName, email,
                                found.message(), found.field())))
                        .orElseGet(() -> registerRemoteThenLocal(username, displayName, email,
                                request.getPassword(), role, planPurpose)))
                .onErrorResume(ex -> {
                    log.error("Self-registration failed for [{}]", username, ex);
                    return Mono.just(response(HttpStatus.SERVICE_UNAVAILABLE, username, displayName, email,
                            "The account service is unreachable right now. Please try again in a moment."));
                });
    }

    /**
     * Duplicate account guard, in the order the register screen asks for the
     * values: username, then email, then full name.
     *
     * <p>
     * All three are compared against publiclink-db.user, which is the account
     * directory: it is the only place an email is stored and the only one
     * searchable by display name. The username is checked against the account
     * service too, further down, because an account can exist there without a
     * local row.
     *
     * @return the first clash, or an empty Optional when the account may be
     *         created. Emptiness here carries a meaning, so no signal may be
     *         dropped: a {@code Mono} that completes without a value would
     *         silently skip the registration itself.
     */
    private Mono<Optional<DuplicateAccount>> firstDuplicate(String username, String email, String name) {
        return Mono.fromCallable(() -> {
            if (coreUserService.findUserEntityByUsername(username).isPresent()) {
                return Optional.of(new DuplicateAccount(FIELD_USERNAME, TAKEN_MESSAGE));
            }
            // Optional values are compared only when they were given: a blank
            // email is absent rather than duplicated, and a blank name falls back
            // to the username already checked above.
            if (!email.isBlank() && coreUserService.isEmailInUse(email)) {
                return Optional.of(new DuplicateAccount(FIELD_EMAIL, EMAIL_TAKEN_MESSAGE));
            }
            if (!name.isBlank() && coreUserService.isNameInUse(name)) {
                return Optional.of(new DuplicateAccount(FIELD_NAME, NAME_TAKEN_MESSAGE));
            }
            // Typed explicitly: the two Optional.of(...) branches and this one
            // have to unify for the reactive signature, and the IDE compiler does
            // not infer the element type from the method's return type.
            return Optional.<DuplicateAccount>empty();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<ResponseEntity<SelfRegisterResponse>> registerRemoteThenLocal(String username, String name,
            String email, String encryptedPassword, String role, String planPurpose) {
        return operatedUserClient.findUserByUsername(new FindUserRequest(username))
                .timeout(REMOTE_TIMEOUT)
                // any answer means the account service already knows the name
                .flatMap(found -> Mono.just(
                        response(HttpStatus.CONFLICT, username, name, email, TAKEN_MESSAGE, FIELD_USERNAME)))
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
     *
     * <p>
     * Only the username and the password are required. Name and email are
     * optional: a blank name falls back to the username, and a blank email is
     * simply absent. Whatever is given is still length- and (for the email)
     * shape-checked, so a typo is caught before the account exists.
     */
    private String firstProblem(String username, String name, String email, String encryptedPassword) {
        if (username.isBlank()) {
            return "Username is required.";
        }
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            return "Username must be 3-30 characters: letters, numbers, dot, dash or underscore.";
        }
        if (name.length() > MAX_NAME_LENGTH) {
            return "Name must be at most 100 characters.";
        }
        if (email.length() > MAX_EMAIL_LENGTH) {
            return "The email address must be at most 100 characters.";
        }
        if (!email.isBlank() && !EMAIL_PATTERN.matcher(email).matches()) {
            return "Please enter a valid email address.";
        }
        if (encryptedPassword == null || encryptedPassword.isBlank()) {
            return "A password is required.";
        }
        return null;
    }

    private ResponseEntity<SelfRegisterResponse> response(HttpStatus status, String username, String name,
            String email, String message) {
        return response(status, username, name, email, message, null);
    }

    private ResponseEntity<SelfRegisterResponse> response(HttpStatus status, String username, String name,
            String email, String message, String field) {
        SelfRegisterResponse body = new SelfRegisterResponse();
        body.setUsername(username);
        body.setName(name);
        body.setEmail(email);
        body.setMessage(message);
        body.setField(field);
        return ResponseEntity.status(status).<SelfRegisterResponse>body(body);
    }

    private String trimOf(String value) {
        return value == null ? "" : value.trim();
    }
}
