import assert from 'node:assert/strict'
import test from 'node:test'
import { indexPluginRuntimeModules, resolvePluginRuntimeRoute } from './plugin-route-runtime'

const modules = indexPluginRuntimeModules([{
  pluginCode: 'minecraft-server',
  moduleName: 'minecraftServer',
  entry: '/plugins/minecraft-server/remoteEntry.js',
  sdkVersion: '1.2.0',
  routes: [],
}])

test('binds the persisted menu component to its enabled remote module', () => {
  const runtime = resolvePluginRuntimeRoute({
    pluginCode: 'minecraft-server',
    pluginModuleName: 'minecraftServer',
  }, 'minecraft-server/CustomAdmin', modules, '1.0.0')

  assert.deepEqual(runtime, {
    pluginCode: 'minecraft-server',
    component: 'minecraft-server/CustomAdmin',
    entry: '/plugins/minecraft-server/remoteEntry.js',
    moduleName: 'minecraftServer',
    sdkVersion: '1.2.0',
  })
})

test('keeps Layout as a normal system layout component', () => {
  assert.equal(resolvePluginRuntimeRoute({
    pluginCode: 'minecraft-server',
    pluginModuleName: 'minecraftServer',
  }, 'Layout', modules), undefined)
})

test('falls back to the default remote entry when the manifest is unavailable', () => {
  assert.deepEqual(resolvePluginRuntimeRoute({
    pluginCode: 'disabled-plugin',
    pluginModuleName: 'disabledModule',
  }, 'disabled/Page', modules), {
    pluginCode: 'disabled-plugin',
    component: 'disabled/Page',
    entry: undefined,
    moduleName: 'disabledModule',
    sdkVersion: undefined,
  })
})
