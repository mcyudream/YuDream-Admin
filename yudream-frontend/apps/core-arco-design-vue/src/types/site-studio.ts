export interface StudioNode {
  id: string
  type: string
  tag: string
  text?: string
  attributes: Record<string, string>
  styles: Record<string, string>
  responsive?: { tablet?: Record<string, string>, mobile?: Record<string, string> }
  children: StudioNode[]
}

export interface StudioPage {
  id: string
  name: string
  path: string
  root: StudioNode[]
}

export interface StudioAsset {
  id: string
  name: string
  url: string
  mimeType: string
  kind: 'image' | 'video' | 'audio' | 'font' | 'file'
}

export interface SiteDocument {
  schemaVersion: 1
  id: string
  name: string
  theme: { tokens: Record<string, string>, header: StudioNode[], footer: StudioNode[] }
  pages: StudioPage[]
  assets: StudioAsset[]
}

/** A collection is a virtual root: it cannot itself be removed or moved. */
export type StudioContainer = { pageId: string } | { region: 'header' | 'footer' } | { parentId: string }
export type StudioCommand
  = | { type: 'batch', commands: StudioCommand[] }
    | { type: 'set-text', nodeId: string, text: string }
    | { type: 'update-node', nodeId: string, patch: Partial<Pick<StudioNode, 'type' | 'tag' | 'text' | 'attributes' | 'styles' | 'responsive' | 'children'>> }
    | { type: 'set-style', nodeId: string, styles: Record<string, string | null>, breakpoint?: 'desktop' | 'tablet' | 'mobile' }
    | { type: 'insert', target: StudioContainer, node: StudioNode, index?: number }
    | { type: 'remove', nodeId: string }
    | { type: 'move', nodeId: string, target: StudioContainer, index?: number }
    | { type: 'duplicate', nodeId: string }
    | { type: 'set-theme', tokens?: Record<string, string>, header?: StudioNode[], footer?: StudioNode[] }
    | { type: 'add-page', page: StudioPage }
    | { type: 'update-page', pageId: string, patch: Partial<Pick<StudioPage, 'name' | 'path' | 'root'>> }
    | { type: 'remove-page', pageId: string }
    | { type: 'set-assets', assets: StudioAsset[] }

export interface StudioCompileOptions { includeChrome?: boolean }

export interface StudioPackage {
  type: 'yudream-site-package'
  version: 1
  kind: 'theme' | 'site'
  document: SiteDocument
  assets: { id: string, mode: 'embedded' | 'reference', url: string, mimeType: string }[]
}

/** Structural model only. The editor lifecycle belongs exclusively to YdPageBuilder. */
export interface StudioGrapesModel {
  type?: string
  tagName?: string
  content?: string
  attributes?: Record<string, string>
  style?: Record<string, string>
  components?: StudioGrapesModel[]
}
