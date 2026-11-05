import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import PublicGuide from './PublicGuide.vue'
import { publicApi } from '../api/client'

vi.mock('../api/client', () => ({
  publicApi: {
    bootstrap: vi.fn(),
  },
  ApiError: class ApiError extends Error {
    status = 500
  },
}))

const bootstrap = {
  points: [
    {
      id: 1,
      name: '北门',
      aliases: [],
      category: 'GATE',
      description: '校园北侧入口',
      x: 0,
      z: 0,
      buildingHeight: 4,
      colorHex: '#7d9a8b',
      modelKey: 'gate',
      roadNodeId: 1,
      status: 'PUBLISHED',
      verificationStatus: 'VERIFIED',
      sortOrder: 1,
    },
  ],
  buildings: [],
  roadNodes: [],
  roadEdges: [],
  cameras: [],
  tours: [
    {
      id: 1,
      key: 'sample-tour',
      name: '校园参观路线',
      summary: '从北门出发的演示路线',
      audience: '访客',
      estimatedMinutes: 10,
      verificationStatus: 'VERIFIED',
      stops: [],
    },
  ],
  config: {
    avatarSpeechRate: 1,
    avatarVolume: 1,
    roamSpeed: 1,
    cameraFlightSeconds: 1,
    highlightMs: 4000,
    autoSpeak: false,
    mode: 'DEMO',
    notice: '演示数据',
  },
  dataVersion: 'test',
}

function installMatchMedia(matches: boolean) {
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      matches,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  })
}

async function mountGuide(matches = false) {
  installMatchMedia(matches)
  vi.mocked(publicApi.bootstrap).mockResolvedValue(bootstrap)
  const wrapper = mount(PublicGuide, {
    global: {
      stubs: {
        CampusScene: true,
        AvatarCompanion: true,
        ChatPanel: true,
      },
    },
  })
  await flushPromises()
  return wrapper
}

describe('PublicGuide sidebars', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('collapses and restores the points sidebar', async () => {
    const wrapper = await mountGuide()
    const pointsRail = wrapper.find('.location-rail')
    const toggle = wrapper.find('.location-rail .rail-collapse')

    expect(pointsRail.classes()).not.toContain('collapsed')
    expect(wrapper.find('.location-rail ol').exists()).toBe(true)

    await toggle.trigger('click')
    expect(pointsRail.classes()).toContain('collapsed')
    expect(wrapper.find('.location-rail .rail-body').exists()).toBe(false)

    await wrapper.find('.location-rail .rail-collapse').trigger('click')
    expect(pointsRail.classes()).not.toContain('collapsed')
    expect(wrapper.find('.location-rail .rail-body').exists()).toBe(true)
  })

  it('collapses and restores the tours sidebar on desktop', async () => {
    const wrapper = await mountGuide()
    const toursRail = wrapper.find('.tour-rail')

    expect(toursRail.classes()).not.toContain('collapsed')
    expect(wrapper.find('.tour-list').exists()).toBe(true)

    await wrapper.find('.tour-rail .collapse-button').trigger('click')
    expect(toursRail.classes()).toContain('collapsed')
    expect(wrapper.find('.tour-list').exists()).toBe(false)

    await wrapper.find('.tour-rail .collapse-button').trigger('click')
    expect(toursRail.classes()).not.toContain('collapsed')
    expect(wrapper.find('.tour-list').exists()).toBe(true)
  })

  it('switches between points and tours on mobile', async () => {
    const wrapper = await mountGuide(true)
    expect(wrapper.find('.tour-rail').classes()).toContain('collapsed')

    await wrapper.find('.tour-rail .collapse-button').trigger('click')

    expect(wrapper.find('.tour-rail').classes()).not.toContain('collapsed')
    expect(wrapper.find('.location-rail').classes()).toContain('collapsed')
  })
})
