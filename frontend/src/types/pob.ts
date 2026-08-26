export interface GemDto {
  name: string
  skillId: string | null
  level: number
  quality: number
  enabled: boolean
}

export interface ItemDto {
  id: number
  rarity: string
  name: string
  base: string
  mods: string[]
}

export interface PobNormalized {
  level: number | null
  className: string | null
  ascendancy: string | null
  gems: GemDto[]
  passiveNodeIds: number[]
  treeVersion: string | null
  overrides: string[]
  items: ItemDto[]
  gear: Record<string, string>
  config: Record<string, string>
  stats: Record<string, number>
}

export interface PobBuildDto {
  scope: 'current' | 'target'
  pastebinUrl: string
  versionHash: string
  parsed: PobNormalized
}

export interface DiffEntry {
  category: string
  message: string
}

export interface PassiveNodeInfo {
  id: number
  name: string
  effects: string[]
  icon: string | null
  keystone: boolean
  notable: boolean
}

export interface DiffReport {
  entries: DiffEntry[]
  missingPassives: PassiveNodeInfo[]
  levelGap: number
}