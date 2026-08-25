package com.poenexus.auth

data class UserDto(
    val id: String,
    val email: String
)

data class AuthResponse(
    val accessToken: String,
    val user: UserDto
)

data class ApiError(
    val code: String,
    val message: String
)