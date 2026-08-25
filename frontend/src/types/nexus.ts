export interface NexusDto {
  id: string
  name: string
  description: string | null
  leaderId: string
  memberCount?: number
}

export interface MemberDto {
  userId: string
  email: string
  role: string
  joinedAt: string
}

export interface NexusDetails {
  nexus: NexusDto
  members: MemberDto[]
}

export interface InviteDto {
  code: string
  expiresAt: string
}