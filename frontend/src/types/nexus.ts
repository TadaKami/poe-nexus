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

export interface MemberSynergyDto {
  userId: string
  email: string
  hasBuild: boolean
  auras: string[]
  curses: string[]
  aura: AuraStatsDto | null
}

export interface SynergyDto {
  members: MemberSynergyDto[]
  auraCounts: Record<string, number>
  curseCounts: Record<string, number>
  duplicates: string[]
}

export interface AuraStatsDto {
  auraBot: boolean
  auraEffect: number
  areaEffect: number
  reservationEff: number
  fireResist: number
  coldResist: number
  lightResist: number
  chaosResist: number
  maxResist: number
}