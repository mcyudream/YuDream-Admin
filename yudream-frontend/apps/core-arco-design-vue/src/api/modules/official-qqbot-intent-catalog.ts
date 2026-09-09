export interface OfficialQqBotIntent {
  code: string
  bit: number
  label: string
  description: string
  group: string
}

export interface OfficialQqBotIntentGroup {
  label: string
  intents: OfficialQqBotIntent[]
}

export const OFFICIAL_QQ_BOT_INTENTS: OfficialQqBotIntent[] = [
  { code: 'GUILDS', bit: 1 << 0, label: '频道', description: '频道创建/更新/删除、子频道变更', group: '频道' },
  { code: 'GUILD_MEMBERS', bit: 1 << 1, label: '频道成员', description: '频道成员进出与资料变更', group: '频道' },
  { code: 'GUILD_MESSAGES', bit: 1 << 9, label: '频道消息（私域）', description: '文字子频道全量消息，仅私域机器人', group: '频道' },
  { code: 'GUILD_MESSAGE_REACTIONS', bit: 1 << 10, label: '频道表情表态', description: '频道消息表情添加/移除', group: '频道' },
  { code: 'DIRECT_MESSAGE', bit: 1 << 12, label: '频道私信', description: '频道私信创建与删除', group: '频道' },
  { code: 'OPEN_FORUMS_EVENT', bit: 1 << 18, label: '论坛（公域）', description: '公域论坛帖子/回复/评论', group: '论坛与音频' },
  { code: 'AUDIO_OR_LIVE_CHANNEL_MEMBER', bit: 1 << 19, label: '音视频/直播成员', description: '音视频、直播子频道成员进出', group: '论坛与音频' },
  { code: 'GROUP_AND_C2C_EVENT', bit: 1 << 25, label: '群与单聊', description: '群@/群消息、单聊、入退群、好友变更等', group: '群与单聊' },
  { code: 'INTERACTION', bit: 1 << 26, label: '互动事件', description: '按钮、键盘、指令面板点击', group: '互动' },
  { code: 'MESSAGE_AUDIT', bit: 1 << 27, label: '消息审核', description: '频道消息审核通过/不通过', group: '频道' },
  { code: 'FORUMS_EVENT', bit: 1 << 28, label: '论坛（私域）', description: '私域论坛帖子/回复/评论', group: '论坛与音频' },
  { code: 'AUDIO_ACTION', bit: 1 << 29, label: '音频动作', description: '音频开始/结束/上麦/下麦', group: '论坛与音频' },
  { code: 'PUBLIC_GUILD_MESSAGES', bit: 1 << 30, label: '频道@消息（公域）', description: '公域文字子频道 @机器人 消息', group: '频道' },
]

const GROUP_ORDER = ['群与单聊', '互动', '频道', '论坛与音频']

export const OFFICIAL_QQ_BOT_INTENT_GROUPS: OfficialQqBotIntentGroup[] = GROUP_ORDER.map(label => ({
  label,
  intents: OFFICIAL_QQ_BOT_INTENTS.filter(intent => intent.group === label),
}))

export const RECOMMENDED_OFFICIAL_INTENTS = (1 << 25) | (1 << 26) | (1 << 30)

export const ALL_OFFICIAL_INTENTS = OFFICIAL_QQ_BOT_INTENTS.reduce((mask, intent) => mask | intent.bit, 0)

export function officialIntentSelected(mask: number | undefined, bit: number) {
  return ((mask ?? 0) & bit) === bit
}

export function selectedOfficialIntentCount(mask: number | undefined) {
  return OFFICIAL_QQ_BOT_INTENTS.filter(intent => officialIntentSelected(mask, intent.bit)).length
}

export function toggleOfficialIntent(mask: number | undefined, bit: number, checked: boolean) {
  const current = mask ?? 0
  return checked ? current | bit : current & ~bit
}

export function setOfficialIntentGroup(mask: number | undefined, bits: number[], selectedBits: number[]) {
  const groupMask = bits.reduce((value, bit) => value | bit, 0)
  const selectedMask = selectedBits.reduce((value, bit) => value | bit, 0)
  return ((mask ?? 0) & ~groupMask) | selectedMask
}
