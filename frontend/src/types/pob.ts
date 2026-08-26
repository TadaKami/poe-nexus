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

export interface GearScoreDto {
  slot: string
  currentName: string | null
  targetName: string
  score: number
  missingMods: string[]
}

export interface TreeNodeDto {
  id: number
  x: number
  y: number
  kind: 'normal' | 'notable' | 'keystone' | 'mastery'
  icon: string | null
  name: string | null
}

export interface TreePayload {
  nodes: TreeNodeDto[]
  edges: number[][]
}

export interface DiffReport {
  entries: DiffEntry[]
  missingPassives: PassiveNodeInfo[]
  missingPassiveIds: number[]
  gearScores: GearScoreDto[]  
  levelGap: number
}