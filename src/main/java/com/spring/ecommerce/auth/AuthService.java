package com.spring.ecommerce.auth;

import com.spring.ecommerce.auth.dto.SignupRequest;
import com.spring.ecommerce.auth.dto.SignupResponse;

public interface AuthService 
{
    SignupResponse register(SignupRequest request);
}
