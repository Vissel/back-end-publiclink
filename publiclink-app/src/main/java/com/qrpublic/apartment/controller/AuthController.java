package com.qrpublic.apartment.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.qrpublic.apartment.authentication.service.JwtService;
import com.qrpublic.apartment.constant.CommonConstant;
import com.qrpublic.apartment.entity.Request;
import com.qrpublic.apartment.requestmodel.AuthToken;
import com.qrpublic.apartment.requestmodel.LoginDTO;
import com.qrpublic.apartment.requestmodel.RegisterUserDTO;
import com.qrpublic.apartment.requestmodel.RoleEnum;
import com.qrpublic.apartment.service.RequestService;
import com.qrpublic.apartment.service.SaleEnvironmentService;
import com.qrpublic.apartment.service.UserService;

import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/auth")
public class AuthController {
	@Autowired
	private AuthenticationManager authenticationManager;

	@Autowired
	private JwtService jwtService;
	@Value("${jwt.url.expired}")
	private long ACCESS_TOKEN_VALIDITY; // 2 days as default

	@Autowired
	private UserService userService;
	@Autowired
	private RequestService requestService;
	@Autowired
	private SaleEnvironmentService envService;

	@PostMapping("/login")
	public ResponseEntity<AuthToken> login(@RequestBody LoginDTO loginDTO) {
		Authentication authentication = authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(loginDTO.getInputUsername(), loginDTO.getInputPassword()));
		UserDetails user = (UserDetails) authentication.getPrincipal();
		String token;

		// For seller
		if (user.getAuthorities().iterator().next().getAuthority().equals(RoleEnum.SELLER.getRole())) {
			token = jwtService.generateTokenByValidTime(user.getUsername(), RoleEnum.SELLER.getRole(),
					ACCESS_TOKEN_VALIDITY);
		} else if (user.getAuthorities().iterator().next().getAuthority().equals(RoleEnum.ADMIN.getRole())) {
			token = jwtService.generateToken(user.getUsername(),
					user.getAuthorities().iterator().next().getAuthority());
		} else {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new AuthToken(CommonConstant.EMPTY));
		}

		AuthToken authRes = new AuthToken(token);
		authRes.setUsername(user.getUsername());
		authRes.setRole(user.getAuthorities().iterator().next().getAuthority());
		SecurityContextHolder.getContext().setAuthentication(authentication);
		return ResponseEntity.ok(authRes);
	}

	/**
	 * Check authentication in AuthenProvider
	 * 
	 * @param headers
	 * @return
	 */
	@GetMapping("/check-auth")
	public ResponseEntity<?> checkAuth(@RequestHeader Map<String, String> headers) {
		final String bearerToken = headers.get("authorization");
		if (bearerToken != null && jwtService.isHeaderTokenValid(headers.get("authorization"))) {
			// User is authenticated
			final String token = bearerToken.substring(7);
			Claims body = jwtService.extractClaims(token);
			String username = jwtService.extractSubject(token);
			AuthToken authResponse = new AuthToken();
			authResponse.setUsername(username);
			authResponse.setRole(body.get("role").toString());
			return ResponseEntity.ok(authResponse);
		} else {
			// User is not authenticated
			// Spring Security's filter chain will likely already send 401,
			// but this endpoint provides a clear check.
			return ResponseEntity.status(401).body("Not Authenticated");
		}
	}

	/**
	 * Register seller with password
	 * 
	 * @param registerDTO
	 * @return
	 */
	@PostMapping("/sellerRegister")
	public ResponseEntity<String> sellerRegister(@RequestBody RegisterUserDTO registerDTO, @RequestParam String reqId) {
		try {
			if (userService.saveUser(registerDTO)) {
				Request request = requestService.saveAuthenticatedRequest(Long.valueOf(reqId));
				if (request != null && request.isAuthenticated()) {
					// generate token
					String sellerToken = jwtService.generateTokenByValidTime(request.getSellerId().getUserName(),
							RoleEnum.SELLER.getRole(), ACCESS_TOKEN_VALIDITY);
					String resLink = envService.getPublicLinkBy(request);
					HttpHeaders headers = new HttpHeaders();
					headers.add("token", sellerToken);
					return ResponseEntity.status(HttpStatus.OK).headers(headers).body(resLink);
				}
			}
		} catch (Exception e) {
			log.error("Exception:{}", e.getMessage());
		}
		return ResponseEntity.badRequest().body("Register failure");
	}

	@GetMapping("/testHeader")
	public ResponseEntity<String> testHeader() {
		return ResponseEntity.status(HttpStatus.OK).header("token", "abctoken").body("testHeader");
	}

    @GetMapping("/public-key")
    public ResponseEntity<ByteArrayResource> getPublicKey() {
        try {
            // Read from /Users/user/.openssl/authpub.pem
            return ResponseEntity.ok(new ByteArrayResource(Files.readAllBytes(Paths.get("/Users/user/.openssl/authpub.pem"))));
        }catch (IOException | OutOfMemoryError e){
            return ResponseEntity.status(HttpStatus.NO_CONTENT).body(null);
        }
    }
}
