import type { Editor, EditorConfig } from 'grapesjs'

export interface YdPageBuilderSnapshot {
  htmlContent: string
  cssContent: string
  builderProjectJson: string
}

export type YdPageBuilderOptions = Omit<EditorConfig, 'container' | 'components' | 'style' | 'projectData' | 'storageManager'>

export interface YdPageBuilderAsset {
  src: string
  type?: string
  name?: string
  [key: string]: unknown
}

export type YdPageBuilderUploadHandler = (files: File[]) => Promise<YdPageBuilderAsset[]>
export type YdPageBuilderSetupHandler = (editor: Editor) => void | Promise<void>

export interface YdPageBuilderEditorFacade {
  readonly editor: Editor
  getSnapshot: () => YdPageBuilderSnapshot
  loadProjectData: (project: Record<string, unknown>) => void
  setComponents: (html: string) => void
  setStyle: (css: string) => void
  dispose: () => void
}

export interface YdPageBuilderInstance {
  getEditor: () => YdPageBuilderEditorFacade | null
  dispose: () => void
}
