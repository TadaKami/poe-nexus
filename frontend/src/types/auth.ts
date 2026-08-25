export interface UserDto{
    id: string;
    email: string;
}

export interface AuthResponse {
    accessToken: string;
    user: UserDto
}