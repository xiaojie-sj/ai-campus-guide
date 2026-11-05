import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AvatarCompanion from './AvatarCompanion.vue'

describe('AvatarCompanion', () => {
  it('opens the knowledge chat from the avatar button', async () => {
    const wrapper = mount(AvatarCompanion, {
      props: {
        active: false,
      },
    })

    await wrapper.find('.avatar-trigger').trigger('click')

    expect(wrapper.emitted('activate')).toHaveLength(1)
    expect(wrapper.find('.avatar-trigger').attributes('aria-expanded')).toBe('false')
  })
})
