/**
 * Arco 原生组件 → Fa/Yd 组件体系的替代映射表。
 * eslint 规则 yudream/prefer-fa-component 与 scripts/audit-ui.mjs 共用，
 * 新增 Fa 组件或发现新的 Arco 用法时在此补充。
 *
 * 只收录存在明确 Fa 等价物的组件；Form/Grid/Tree/Menu 等无 Fa 对应的不在此列。
 */
export const ARCO_TO_FA_MAP = {
  Alert: 'FaAlert',
  Avatar: 'FaAvatar',
  Badge: 'FaBadge',
  Button: 'FaButton',
  Card: 'FaCard',
  Checkbox: 'FaCheckbox',
  CheckboxGroup: 'FaCheckboxGroup',
  Collapse: 'FaCollapsible',
  CollapseItem: 'FaCollapsible',
  Descriptions: 'FaDescriptions',
  DescriptionsItem: 'FaDescriptions 的 items 配置',
  Divider: 'FaDivider',
  Drawer: 'FaDrawer / useFaDrawer',
  Dropdown: 'FaDropdown',
  Doption: 'FaDropdown 的 items 配置',
  Empty: '中性空态块（如 text-secondary-foreground/50 的占位 div）',
  Image: 'FaImagePreview',
  ImagePreview: 'useFaImagePreview',
  ImagePreviewGroup: 'FaImagePreview',
  Input: 'FaInput',
  InputNumber: 'FaNumberField',
  InputOtp: 'FaInputOTP',
  InputPassword: 'FaInput type="password"',
  InputSearch: 'FaInput',
  Kbd: 'FaKbd',
  Link: 'FaButton variant="link"',
  Modal: 'FaModal / useFaModal',
  Option: 'FaSelect 的 options 配置',
  PageHeader: 'FaPageHeader',
  Pagination: 'FaPagination',
  Popconfirm: 'FaPopover / useFaModal 确认框',
  Popover: 'FaPopover',
  Progress: 'FaProgress',
  Radio: 'FaRadioGroup',
  RadioGroup: 'FaRadioGroup',
  Scrollbar: 'FaScrollArea',
  Select: 'FaSelect',
  Slider: 'FaSlider',
  Spin: 'FaProgress / 加载占位',
  Switch: 'FaSwitch',
  Table: 'FaTable / FaResponsiveTable',
  TabPane: 'FaTabs 的 items 配置',
  Tabs: 'FaTabs',
  Tag: 'FaTag',
  Textarea: 'FaTextarea',
  Tooltip: 'FaTooltip',
  Upload: 'FaFileUpload / FaImageUpload',
}

/**
 * 脚本侧 API（非组件）：import { Message } from '@arco-design/web-vue'
 */
export const ARCO_SCRIPT_API_MAP = {
  Message: 'useFaToast',
  Notification: 'useFaToast',
}

export function toKebabCase(name) {
  return name.replace(/([a-z0-9])([A-Z])/g, '$1-$2').toLowerCase()
}

/** kebab-case 模板标签（去掉 a- 前缀后） → Fa 替代 */
export const ARCO_TAG_TO_FA_MAP = Object.fromEntries(
  Object.entries(ARCO_TO_FA_MAP).map(([name, fa]) => [toKebabCase(name), fa]),
)
