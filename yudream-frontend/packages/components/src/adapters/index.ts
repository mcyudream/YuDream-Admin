import FaAlert from '../basic/alert/index.vue'
import FaAvatar from '../basic/avatar/index.vue'
import FaBadge from '../basic/badge/index.vue'
import FaButtonGroup from '../basic/button-group/index.vue'
import FaButton from '../basic/button/index.vue'
import FaCard from '../basic/card/index.vue'
import FaCheckboxGroup from '../basic/checkbox-group/index.vue'
import FaCheckbox from '../basic/checkbox/index.vue'
import FaCollapsible from '../basic/collapsible/index.vue'
import FaContextMenu from '../basic/context-menu/index.vue'
import FaDescriptions from '../basic/descriptions/index.vue'
import FaDivider from '../basic/divider/index.vue'
import FaDrawer from '../basic/drawer/index.vue'
import FaDropdown from '../basic/dropdown/index.vue'
import FaFileUpload from '../basic/file-upload/index.vue'
import FaFixedBar from '../basic/fixed-bar/index.vue'
import FaHoverCard from '../basic/hover-card/index.vue'
import FaIcon from '../basic/icon/index.vue'
import FaImagePreview from '../basic/image-preview/index.vue'
import FaImageUpload from '../basic/image-upload/index.vue'
import FaInputOTP from '../basic/input-otp/index.vue'
import FaInput from '../basic/input/index.vue'
import FaKbdGroup from '../basic/kbd-group/index.vue'
import FaKbd from '../basic/kbd/index.vue'
import FaLabel from '../basic/label/index.vue'
import FaModal from '../basic/modal/index.vue'
import FaNumberField from '../basic/number-field/index.vue'
import FaPageHeader from '../basic/page-header/index.vue'
import FaPageMain from '../basic/page-main/index.vue'
import FaPagination from '../basic/pagination/index.vue'
import FaPasswordStrength from '../basic/password-strength/index.vue'
import FaPopover from '../basic/popover/index.vue'
import FaProgress from '../basic/progress/index.vue'
import FaRadioGroup from '../basic/radio-group/index.vue'
import FaResponsiveTable from '../basic/responsive-table/index.vue'
import FaScrollArea from '../basic/scroll-area/index.vue'
import FaSearchBar from '../basic/search-bar/index.vue'
import FaSelect from '../basic/select/index.vue'
import FaSlider from '../basic/slider/index.vue'
import FaSwitch from '../basic/switch/index.vue'
import FaTable from '../basic/table/index.vue'
import FaTabs from '../basic/tabs/index.vue'
import FaTag from '../basic/tag/index.vue'
import FaTextarea from '../basic/textarea/index.vue'
import FaToast from '../basic/toast/index.vue'
import FaTooltip from '../basic/tooltip/index.vue'
import FaTrend from '../basic/trend/index.vue'
import { createYdComponent } from './createYdComponent'
import './adapters.css'

// 复用 Fa 行为、类型与插槽，YD 只增加主题外观层。
export const YdAlert = /* @__PURE__ */ createYdComponent(FaAlert, 'YdAlert', 'alert')
export const YdAvatar = /* @__PURE__ */ createYdComponent(FaAvatar, 'YdAvatar', 'avatar')
export const YdBadge = /* @__PURE__ */ createYdComponent(FaBadge, 'YdBadge', 'badge')
export const YdButtonGroup = /* @__PURE__ */ createYdComponent(FaButtonGroup, 'YdButtonGroup', 'button-group')
export const YdButton = /* @__PURE__ */ createYdComponent(FaButton, 'YdButton', 'button')
export const YdCard = /* @__PURE__ */ createYdComponent(FaCard, 'YdCard', 'card')
export const YdCheckboxGroup = /* @__PURE__ */ createYdComponent(FaCheckboxGroup, 'YdCheckboxGroup', 'checkbox-group')
export const YdCheckbox = /* @__PURE__ */ createYdComponent(FaCheckbox, 'YdCheckbox', 'checkbox')
export const YdCollapsible = /* @__PURE__ */ createYdComponent(FaCollapsible, 'YdCollapsible', 'collapsible')
export const YdContextMenu = /* @__PURE__ */ createYdComponent(FaContextMenu, 'YdContextMenu', 'context-menu')
export const YdDescriptions = /* @__PURE__ */ createYdComponent(FaDescriptions, 'YdDescriptions', 'descriptions')
export const YdDivider = /* @__PURE__ */ createYdComponent(FaDivider, 'YdDivider', 'divider')
export const YdDrawer = /* @__PURE__ */ createYdComponent(FaDrawer, 'YdDrawer', 'drawer')
export const YdDropdown = /* @__PURE__ */ createYdComponent(FaDropdown, 'YdDropdown', 'dropdown')
export const YdFileUpload = /* @__PURE__ */ createYdComponent(FaFileUpload, 'YdFileUpload', 'file-upload')
export const YdFixedBar = /* @__PURE__ */ createYdComponent(FaFixedBar, 'YdFixedBar', 'fixed-bar')
export const YdHoverCard = /* @__PURE__ */ createYdComponent(FaHoverCard, 'YdHoverCard', 'hover-card')
export const YdIcon = /* @__PURE__ */ createYdComponent(FaIcon, 'YdIcon', 'icon')
export const YdImagePreview = /* @__PURE__ */ createYdComponent(FaImagePreview, 'YdImagePreview', 'image-preview')
export const YdImageUpload = /* @__PURE__ */ createYdComponent(FaImageUpload, 'YdImageUpload', 'image-upload')
export const YdInputOTP = /* @__PURE__ */ createYdComponent(FaInputOTP, 'YdInputOTP', 'input-otp')
export const YdInput = /* @__PURE__ */ createYdComponent(FaInput, 'YdInput', 'input')
export const YdKbdGroup = /* @__PURE__ */ createYdComponent(FaKbdGroup, 'YdKbdGroup', 'kbd-group')
export const YdKbd = /* @__PURE__ */ createYdComponent(FaKbd, 'YdKbd', 'kbd')
export const YdLabel = /* @__PURE__ */ createYdComponent(FaLabel, 'YdLabel', 'label')
export const YdModal = /* @__PURE__ */ createYdComponent(FaModal, 'YdModal', 'modal')
export const YdNumberField = /* @__PURE__ */ createYdComponent(FaNumberField, 'YdNumberField', 'number-field')
export const YdPageHeader = /* @__PURE__ */ createYdComponent(FaPageHeader, 'YdPageHeader', 'page-header')
export const YdPageMain = /* @__PURE__ */ createYdComponent(FaPageMain, 'YdPageMain', 'page-main')
export const YdPagination = /* @__PURE__ */ createYdComponent(FaPagination, 'YdPagination', 'pagination')
export const YdPasswordStrength = /* @__PURE__ */ createYdComponent(FaPasswordStrength, 'YdPasswordStrength', 'password-strength')
export const YdPopover = /* @__PURE__ */ createYdComponent(FaPopover, 'YdPopover', 'popover')
export const YdProgress = /* @__PURE__ */ createYdComponent(FaProgress, 'YdProgress', 'progress')
export const YdRadioGroup = /* @__PURE__ */ createYdComponent(FaRadioGroup, 'YdRadioGroup', 'radio-group')
export const YdResponsiveTable = /* @__PURE__ */ createYdComponent(FaResponsiveTable, 'YdResponsiveTable', 'responsive-table')
export const YdScrollArea = /* @__PURE__ */ createYdComponent(FaScrollArea, 'YdScrollArea', 'scroll-area')
export const YdSearchBar = /* @__PURE__ */ createYdComponent(FaSearchBar, 'YdSearchBar', 'search-bar')
export const YdSelect = /* @__PURE__ */ createYdComponent(FaSelect, 'YdSelect', 'select')
export const YdSlider = /* @__PURE__ */ createYdComponent(FaSlider, 'YdSlider', 'slider')
export const YdSwitch = /* @__PURE__ */ createYdComponent(FaSwitch, 'YdSwitch', 'switch')
export const YdTable = /* @__PURE__ */ createYdComponent(FaTable, 'YdTable', 'table')
export const YdTabs = /* @__PURE__ */ createYdComponent(FaTabs, 'YdTabs', 'tabs')
export const YdTag = /* @__PURE__ */ createYdComponent(FaTag, 'YdTag', 'tag')
export const YdTextarea = /* @__PURE__ */ createYdComponent(FaTextarea, 'YdTextarea', 'textarea')
export const YdToast = /* @__PURE__ */ createYdComponent(FaToast, 'YdToast', 'toast')
export const YdTooltip = /* @__PURE__ */ createYdComponent(FaTooltip, 'YdTooltip', 'tooltip')
export const YdTrend = /* @__PURE__ */ createYdComponent(FaTrend, 'YdTrend', 'trend')
