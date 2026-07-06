const components = shared('components')

function shared(name: string) {
  const modules = (globalThis as any).__YUDREAM_PLUGIN_SHARED__
  const module = modules?.[name]
  if (!module) {
    throw new Error(`YuDream plugin shared runtime is missing: ${name}`)
  }
  return module
}

export const FaAlert = components.FaAlert
export const FaAvatar = components.FaAvatar
export const FaBadge = components.FaBadge
export const FaButtonGroup = components.FaButtonGroup
export const FaButton = components.FaButton
export const FaCard = components.FaCard
export const FaCheckboxGroup = components.FaCheckboxGroup
export const FaCheckbox = components.FaCheckbox
export const FaCollapsible = components.FaCollapsible
export const FaContextMenu = components.FaContextMenu
export const FaDescriptions = components.FaDescriptions
export const FaDivider = components.FaDivider
export const useFaDrawer = components.useFaDrawer
export const FaDrawer = components.FaDrawer
export const FaDropdown = components.FaDropdown
export const FaFileUpload = components.FaFileUpload
export const FaFixedBar = components.FaFixedBar
export const FaHoverCard = components.FaHoverCard
export const FaIcon = components.FaIcon
export const useFaImagePreview = components.useFaImagePreview
export const FaImagePreview = components.FaImagePreview
export const FaImageUpload = components.FaImageUpload
export const FaInputOTP = components.FaInputOTP
export const FaInput = components.FaInput
export const FaKbdGroup = components.FaKbdGroup
export const FaKbd = components.FaKbd
export const FaLabel = components.FaLabel
export const useFaModal = components.useFaModal
export const FaModal = components.FaModal
export const FaNumberField = components.FaNumberField
export const FaPageHeader = components.FaPageHeader
export const FaPageMain = components.FaPageMain
export const FaPagination = components.FaPagination
export const FaPasswordStrength = components.FaPasswordStrength
export const FaPopover = components.FaPopover
export const FaProgress = components.FaProgress
export const FaRadioGroup = components.FaRadioGroup
export const FaScrollArea = components.FaScrollArea
export const FaSearchBar = components.FaSearchBar
export const FaSelect = components.FaSelect
export const FaSlider = components.FaSlider
export const FaSwitch = components.FaSwitch
export const FaTable = components.FaTable
export const FaTabs = components.FaTabs
export const FaTag = components.FaTag
export const FaTextarea = components.FaTextarea
export const useFaToast = components.useFaToast
export const FaToast = components.FaToast
export const FaTooltip = components.FaTooltip
export const FaTrend = components.FaTrend

export default components
