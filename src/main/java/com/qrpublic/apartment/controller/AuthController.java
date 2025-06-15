package com.qrpublic.apartment.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
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

import com.qrpublic.apartment.config.JwtUtil;
import com.qrpublic.apartment.entity.Request;
import com.qrpublic.apartment.requestmodel.AuthToken;
import com.qrpublic.apartment.requestmodel.LoginDTO;
import com.qrpublic.apartment.requestmodel.RegisterUserDTO;
import com.qrpublic.apartment.service.RequestService;
import com.qrpublic.apartment.service.SaleEnvironmentService;
import com.qrpublic.apartment.service.UserService;

import io.jsonwebtoken.Claims;

@RestController
@RequestMapping("/auth")
public class AuthController {
	@Autowired
	private AuthenticationManager authenticationManager;

	@Autowired
	private JwtUtil jwtService;

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
		String token = jwtService.generateToken(user.getUsername(),
				user.getAuthorities().iterator().next().getAuthority());
		AuthToken authRes = new AuthToken(token);
		authRes.setUsername(user.getUsername());
		authRes.setRole(jwtService.extractClaims(token).get("role").toString());
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
		String token = headers.get("authorization");
		if (token != null && jwtService.isTokenValid(token.substring(7))) {
			// User is authenticated
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
	@GetMapping("/sellerRegister")
	public ResponseEntity<String> sellerRegister(@RequestBody RegisterUserDTO registerDTO, @RequestParam String reqId) {
		if (userService.saveUser(registerDTO)) {
			Request request = requestService.saveRequest(Long.valueOf(reqId));
			if (request != null) {
				String resLink = envService.getPublicLinkBy(request);
				return ResponseEntity.ok(resLink);
			}
		}
		return ResponseEntity.badRequest().body("Register failure");
	}
}
