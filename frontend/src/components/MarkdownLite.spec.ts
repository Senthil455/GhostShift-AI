import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import MarkdownLite from './MarkdownLite.vue'

function render(content: string) {
  return mount(MarkdownLite, { props: { content } })
}

describe('MarkdownLite', () => {
  it('renders headings, paragraphs, lists, quotes, code, and rules', () => {
    const wrapper = render(
      [
        '# Report',
        '',
        'A short **summary** with `inline code`.',
        '',
        '- first item',
        '- second item',
        '',
        '1. step one',
        '2. step two',
        '',
        '> quoted evidence',
        '',
        '```java',
        'int count = 2;',
        '```',
        '',
        '---',
      ].join('\n'),
    )

    expect(wrapper.find('h1').text()).toBe('Report')
    expect(wrapper.find('p').text()).toContain('A short summary with inline code.')
    expect(wrapper.find('strong').text()).toBe('summary')
    expect(wrapper.find('code').text()).toBe('inline code')
    expect(wrapper.findAll('ul li').map((item) => item.text())).toEqual(['first item', 'second item'])
    expect(wrapper.findAll('ol li').map((item) => item.text())).toEqual(['step one', 'step two'])
    expect(wrapper.find('blockquote').text()).toBe('quoted evidence')
    expect(wrapper.find('pre code').text()).toBe('int count = 2;')
    expect(wrapper.find('hr').exists()).toBe(true)
  })

  it('caps deep headings at h3', () => {
    const wrapper = render('#### Very deep heading')
    expect(wrapper.find('h1').exists()).toBe(false)
    expect(wrapper.find('h2').exists()).toBe(false)
    expect(wrapper.find('h3').text()).toBe('Very deep heading')
  })

  it('only linkifies http(s) targets and escapes raw HTML', () => {
    const wrapper = render('See [the docs](https://example.com/a) or [unsafe](javascript:alert(1)). <script>alert(1)</script>')

    const links = wrapper.findAll('a')
    expect(links).toHaveLength(1)
    expect(links[0].attributes('href')).toBe('https://example.com/a')
    expect(links[0].attributes('rel')).toBe('noopener noreferrer')
    expect(wrapper.html()).not.toContain('<script>')
    expect(wrapper.text()).toContain('<script>alert(1)</script>')
  })

  it('keeps an unclosed code fence in a code block', () => {
    const wrapper = render('```\nunclosed')
    expect(wrapper.find('pre').exists()).toBe(true)
    expect(wrapper.find('pre code').text()).toBe('unclosed')
  })
})
