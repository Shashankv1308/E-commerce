package com.spring.ecommerce.auth;

import com.spring.ecommerce.auth.dto.SignupRequest;
import com.spring.ecommerce.auth.dto.SignupResponse;
import com.spring.ecommerce.exception.BusinessException;
import com.spring.ecommerce.user.Role;
import com.spring.ecommerce.user.User;
import com.spring.ecommerce.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthServiceImpl(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public SignupResponse register(SignupRequest request) {

        // 1. Check email uniqueness
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new BusinessException("Email already registered");
        }

        // 2. Create user
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.USER);

        // 3. Persist
        userRepository.save(user);

        return new SignupResponse("User registered successfully");
    }
}
