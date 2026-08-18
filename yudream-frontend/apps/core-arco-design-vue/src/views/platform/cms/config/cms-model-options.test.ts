import { describe, it } from 'node:test'
import assert from 'node:assert'
import { findCmsModelOption, resolveCmsModelValue, toCmsModelOptions } from './cms-model-options'

describe('cms-model-options', () => {
  it('只保留已配置的 chat 模型并按默认模型优先排序', () => {
    const options = toCmsModelOptions([
      { providerCode: 'openai', providerName: 'OpenAI', modelCode: 'gpt-5', modelName: 'GPT-5', kind: 'chat', configured: true, vision: true, defaultModel: false },
      { providerCode: 'openai', providerName: 'OpenAI', modelCode: 'gpt-5-embed', modelName: 'Embed', kind: 'embedding', configured: true, vision: false, defaultModel: false },
      { providerCode: 'deepseek', providerName: 'DeepSeek', modelCode: 'v3', modelName: 'V3', kind: 'chat', configured: false, vision: false, defaultModel: false },
      { providerCode: 'qwen', providerName: '通义', modelCode: 'max', modelName: 'Max', kind: 'chat', configured: true, vision: false, defaultModel: true },
    ])
    assert.deepEqual(options.map(option => option.value), ['qwen/max', 'openai/gpt-5'])
    assert.equal(options[0].label, '通义 · Max')
  })

  it('解析选中值：保留有效值，失效时回退默认模型', () => {
    const options = toCmsModelOptions([
      { providerCode: 'a', providerName: 'A', modelCode: 'm1', modelName: 'M1', kind: 'chat', configured: true, vision: false, defaultModel: false },
      { providerCode: 'b', providerName: 'B', modelCode: 'm2', modelName: 'M2', kind: 'chat', configured: true, vision: false, defaultModel: true },
    ])
    assert.equal(resolveCmsModelValue(options, 'a/m1'), 'a/m1')
    assert.equal(resolveCmsModelValue(options, 'gone/x'), 'b/m2')
    assert.equal(resolveCmsModelValue([], 'a/m1'), '')
  })

  it('按组合值查找选项', () => {
    const options = toCmsModelOptions([
      { providerCode: 'a', providerName: 'A', modelCode: 'm1', modelName: 'M1', kind: 'chat', configured: true, vision: true, defaultModel: false },
    ])
    assert.equal(findCmsModelOption(options, 'a/m1')?.modelCode, 'm1')
    assert.equal(findCmsModelOption(options, 'a/none'), null)
  })
})
