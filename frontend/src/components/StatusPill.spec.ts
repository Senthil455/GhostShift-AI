import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import StatusPill from './StatusPill.vue'

describe('StatusPill', () => {
  it('renders a humanized status with the inferred tone', () => {
    const wrapper = mount(StatusPill, { props: { status: 'WAITING_FOR_APPROVAL' } })

    expect(wrapper.text()).toBe('Waiting For Approval')
    expect(wrapper.classes()).toContain('pill--warning')
  })

  it.each([
    ['COMPLETED', 'success'],
    ['RUNNING', 'info'],
    ['FAILED', 'danger'],
    ['ARCHIVED', 'neutral'],
    ['SOMETHING_NEW', 'neutral'],
  ])('maps %s to the %s tone', (status, tone) => {
    const wrapper = mount(StatusPill, { props: { status } })
    expect(wrapper.classes()).toContain(`pill--${tone}`)
  })

  it('lets an explicit tone and label override the status defaults', () => {
    const wrapper = mount(StatusPill, {
      props: { status: 'FAILED', tone: 'success', label: 'Verified' },
    })

    expect(wrapper.text()).toBe('Verified')
    expect(wrapper.classes()).toContain('pill--success')
  })

  it('shows the dot only when requested', async () => {
    const wrapper = mount(StatusPill, { props: { status: 'OK' } })
    expect(wrapper.find('.pill__dot').exists()).toBe(false)

    await wrapper.setProps({ dot: true })
    expect(wrapper.find('.pill__dot').exists()).toBe(true)
  })
})
