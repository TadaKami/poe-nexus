package com.poenexus.nexus

data class CreateNexusRequest(
    val name: String?,
    val description: String?
)

data class JoinRequest(
    val code: String?
)

data class RoleChangeRequest(
    val role: String?
)

data class NexusDto(
    val id: String,
    val name: String,
    val description: String?,
    val leaderId: String,
    val memberCount: Int?
)

data class MemberDto(
    val userId: String,
    val email: String,
    val role: String,
    val joinedAt: String
)

data class NexusDetailsDto(
    val nexus: NexusDto,
    val members: List<MemberDto>
)

data class InviteDto(
    val code: String,
    val expiresAt: String
)