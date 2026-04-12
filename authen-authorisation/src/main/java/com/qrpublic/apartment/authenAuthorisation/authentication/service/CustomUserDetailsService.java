package com.qrpublic.apartment.authenAuthorisation.authentication.service;

import com.qrpublic.apartment.authenAuthorisation.user.dataEntity.User;
import com.qrpublic.apartment.authenAuthorisation.user.repo.UserAuthRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;


@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    UserAuthRepository userAuthRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userAuthRepository.findByUserName(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));

        List<GrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority(user.getType()));

        return new org.springframework.security.core.userdetails.User(user.getUserName(), user.getTempPassword(),
                authorities);
    }

}
