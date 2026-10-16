import io

p = r'yudream-frontend/packages/components/src/adapters/adapters.css'
with io.open(p, encoding='utf-8', newline='') as f:
    txt = f.read()
nl = '\r\n' if '\r\n' in txt else '\n'

old = nl.join([
    '.yd-tabs [role="tablist"],',
    '.yd-tabs [role="tab"] {',
    '  border-radius: var(--yd-control-radius);',
    '}',
    '.yd-tabs [role="tab"] {',
    '  min-height: var(--yd-control-height);',
    '  font-size: var(--yd-control-font-size);',
    '  transition: background var(--yd-motion-duration-fast), box-shadow var(--yd-motion-duration-fast);',
    '}',
])
new = nl.join([
    '/* 视图切换与按钮同高：列表取控件高度，触发器填满列表，避免活动胶囊溢出容器 */',
    '.yd-tabs [role="tablist"] {',
    '  min-height: var(--yd-control-height);',
    '  border-radius: var(--yd-control-radius);',
    '}',
    '.yd-tabs [role="tab"] {',
    '  border-radius: calc(var(--yd-control-radius) - 4px);',
    '  font-size: var(--yd-control-font-size);',
    '  transition: background var(--yd-motion-duration-fast), box-shadow var(--yd-motion-duration-fast);',
    '}',
])
assert old in txt, 'tabs adapter block not found'
txt = txt.replace(old, new, 1)
with io.open(p, 'w', encoding='utf-8', newline='') as f:
    f.write(txt)
print('adapters tabs fixed')
