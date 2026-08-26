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

data class MemberSynergyDto(
    val userId: String,
    val email: String,
    val hasBuild: Boolean,
    val auras: List<String>,
    val curses: List<String>
)

data class SynergyDto(
    val members: List<MemberSynergyDto>,
    val auraCounts: Map<String, Int>,
    val curseCounts: Map<String, Int>,
    val duplicates: List<String>
)