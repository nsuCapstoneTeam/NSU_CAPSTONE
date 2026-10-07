package com.nsu.capstone.identity.security;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private static final String USER_NOT_FOUND = "User is not available for password login";

    private final UserRepository userRepository;

    public DatabaseUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException(USER_NOT_FOUND));

        if (user.getPasswordHash() == null) {
            throw new UsernameNotFoundException(USER_NOT_FOUND);
        }

        return LoginPrincipal.from(user);
    }
}
