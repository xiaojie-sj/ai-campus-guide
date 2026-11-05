import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ChatPanel from './ChatPanel.vue'

describe('ChatPanel', () => {
  it('emits a trimmed question when the form is submitted', async () => {
    const wrapper = mount(ChatPanel, {
      props: {
        messages: [],
        suggestions: [],
        busy: false,
      },
    })

    await wrapper.find('textarea').setValue('  从校门到图书馆怎么走  ')
    await wrapper.find('form').trigger('submit')

    expect(wrapper.emitted('send')?.[0]).toEqual(['从校门到图书馆怎么走'])
  })

  it('disables suggestions while a request is pending', () => {
    const wrapper = mount(ChatPanel, {
      props: {
        messages: [],
        suggestions: ['介绍一下图书馆'],
        busy: true,
      },
    })

    const suggestion = wrapper.find('.suggestion-strip button')
    expect(suggestion.attributes('disabled')).toBeDefined()
  })

  it('emits close when the console is collapsed', async () => {
    const wrapper = mount(ChatPanel, {
      props: {
        messages: [],
        suggestions: [],
        busy: false,
      },
    })

    await wrapper.find('.console-close').trigger('click')

    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('renders approved media alongside the answer', () => {
    const wrapper = mount(ChatPanel, {
      props: {
        messages: [
          {
            id: 'answer',
            role: 'guide',
            text: '学校大门位于校园南侧。',
            media: [
              {
                id: 1,
                title: '校园平面示意',
                url: '/media/campus-map.png',
                altText: '校园平面位置示意',
              },
            ],
          },
        ],
        suggestions: [],
        busy: false,
      },
    })

    expect(wrapper.find('.message-media img').attributes('src')).toBe('/media/campus-map.png')
    expect(wrapper.find('.message-media figcaption').text()).toContain('校园平面示意')
  })
})
