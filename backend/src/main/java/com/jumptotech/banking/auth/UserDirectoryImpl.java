package com.jumptotech.banking.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class UserDirectoryImpl implements UserDirectory {

    private final UserRepository userRepository;

    public UserDirectoryImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Optional<String> findEmailByUserId(UUID userId) {
        return userRepository.findById(userId).map(User::getEmail);
    }
}
