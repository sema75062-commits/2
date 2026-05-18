package com.example.music_school.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.music_school.entities.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    
    Optional<User> findByEmail(String email);
}
