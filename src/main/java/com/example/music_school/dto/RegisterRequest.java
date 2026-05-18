package com.example.music_school.dto;

import lombok.Data;

@Data
public class RegisterRequest {
    private String email;
    private String password;
}