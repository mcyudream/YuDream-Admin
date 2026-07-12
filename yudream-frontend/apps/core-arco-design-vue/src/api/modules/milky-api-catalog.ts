export interface MilkyApiDefinition {
  name: string
  category: string
}

const catalog: Record<string, string[]> = {
  '账号与资源': ['get_login_info', 'get_impl_info', 'get_user_profile', 'get_cookies', 'get_csrf_token', 'get_resource_temp_url', 'set_avatar', 'set_nickname', 'set_bio', 'send_profile_like', 'get_custom_face_url_list'],
  '会话与消息': ['get_history_messages', 'get_message', 'get_forwarded_messages', 'mark_message_as_read', 'get_peer_pins', 'set_peer_pin', 'send_group_message', 'send_private_message', 'recall_group_message', 'recall_private_message', 'send_group_message_reaction', 'send_group_nudge', 'send_friend_nudge'],
  '好友': ['get_friend_list', 'get_friend_info', 'get_friend_requests', 'accept_friend_request', 'reject_friend_request', 'delete_friend'],
  '群与成员': ['get_group_list', 'get_group_info', 'get_group_member_list', 'get_group_member_info', 'get_group_notifications', 'accept_group_request', 'reject_group_request', 'accept_group_invitation', 'reject_group_invitation', 'quit_group', 'kick_group_member', 'set_group_name', 'set_group_avatar', 'set_group_member_admin', 'set_group_member_card', 'set_group_member_mute', 'set_group_member_special_title', 'set_group_whole_mute'],
  '群公告与精华': ['get_group_announcements', 'send_group_announcement', 'delete_group_announcement', 'get_group_essence_messages', 'set_group_essence_message'],
  '群文件': ['get_group_files', 'get_group_file_download_url', 'upload_group_file', 'delete_group_file', 'rename_group_file', 'move_group_file', 'create_group_folder', 'rename_group_folder', 'delete_group_folder', 'persist_group_file'],
  '私聊文件': ['upload_private_file', 'get_private_file_download_url'],
}

export const milkyApiCatalog: MilkyApiDefinition[] = Object.entries(catalog).flatMap(([category, names]) => names.map(name => ({ name, category })))
