import { defineConfig } from 'vitepress'

export default defineConfig({
  title: "Eclipse Peon AI",
  description: "An Eclipse plugin that brings a lightweight, context-aware LLM assistant directly into the Eclipse workbench",
  srcDir: './docs',
  base: '/',
  lastUpdated: true,
  cleanUrls: true,
  ignoreDeadLinks: true,

  themeConfig: {
    siteTitle: "Eclipse Peon AI",
    logo: '/assets/logo.png',

    nav: [
      { text: 'Home', link: '/' },
      { text: 'Setup', link: '/setup/installation' },
      { text: 'Development', link: '/development/architecture' }
    ],

    sidebar: {
      '/': [
        {
          text: 'Introduction',
          items: [
            { text: 'Overview', link: '/' },
            { text: 'Agents & Skills', link: '/setup/agents-and-skills' },
            { text: 'Commands', link: '/setup/commands' },
            { text: 'Agent Mode', link: '/setup/agent-mode' },
            { text: 'Memory', link: '/peon-memory' }
          ]
        },
        {
          text: 'Setup',
          items: [
            { text: 'Installation', link: '/setup/installation' },
            { text: 'Configuration', link: '/setup/configuration' },
            { text: 'MCP', link: '/setup/mcp-configuration' },
            { text: 'Voice', link: '/setup/voice-config' },
            { text: 'Which model?', link: '/model-checks' },
            { text: 'Qwen tuning', link: '/setup/qwen3627b_lmstudio_optimization' },
            { text: 'llama.cpp', link: '/setup/llama' }
          ]
        },
        {
          text: 'Usage',
          items: [
            { text: 'Keyboard Shortcuts', link: '/usage/keyboard-shortcuts' },
            { text: 'Context Selection', link: '/usage/selections' }
          ]
        },
        {
          text: 'Development',
          items: [
            { text: 'Architecture', link: '/development/architecture' },
            { text: 'Building', link: '/development/building' }
          ]
        },
        {
          text: 'Design',
          items: [
            { text: 'Interaction UI', link: '/design/interaction-design' },
            { text: 'Plan/Dev/Agent Design (WIP)', link: '/design/plan-dev-agent-design' }
          ]
        }
      ]
    },

    socialLinks: [
      { icon: 'github', link: 'https://github.com/sterlp/eclipse-peon-ai' }
    ],

    footer: {
      message: 'Released under the MIT License.',
      copyright: 'Copyright © 2024-Present Paul Sterl'
    },

    search: {
      provider: 'local',
      options: {
        detailedView: true
      }
    }
  },

  markdown: {
    theme: 'material-theme-palenight',
    lineNumbers: true
  }
})
