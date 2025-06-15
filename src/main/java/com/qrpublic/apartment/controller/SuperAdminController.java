package com.qrpublic.apartment.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.qrpublic.apartment.requestmodel.RegisterUserDTO;
import com.qrpublic.apartment.service.UserService;

@RestController
@RequestMapping("/admin/internal")
public class SuperAdminController {

	@Autowired
	UserService userService;

	@PostMapping("/registerUser")
	public ResponseEntity<String> registerUser(@RequestBody RegisterUserDTO userDTO) {

		if (userService.saveUser(userDTO)) {
			return ResponseEntity.ok(new String("Register user " + userDTO.getUserName() + " successfully."));
		}
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new String("Registering got failure!!!"));

	}
}
