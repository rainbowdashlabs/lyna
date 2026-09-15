<script lang="ts" setup>
import {computed, onMounted} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {useAdminGuilds} from '~/composables/useAdminGuilds'

const route = useRoute()
const router = useRouter()
const {guilds, load} = useAdminGuilds()

onMounted(load)

const currentGuildId = computed(() => {
  const match = /^\/admin\/g\/([^/]+)/.exec(route.path)
  return match?.[1] ?? null
})

const currentGuild = computed(() => guilds.value.find(g => g.id === currentGuildId.value) ?? null)
const isOperator = computed(() => guilds.value.some(g => g.role === 'operator'))
const inInstance = computed(() => route.path.startsWith('/admin/instance'))

const sections = computed(() => {
  if (inInstance.value) {
    return [
      {to: '/admin/instance/system', label: 'System'},
      {to: '/admin/instance/appearance', label: 'Appearance'},
      {to: '/admin/instance/operators', label: 'Operators'},
    ]
  }
  if (!currentGuildId.value) return []
  const base = `/admin/g/${currentGuildId.value}`
  return [
    {to: `${base}/products`, label: 'Products'},
    {to: `${base}/licenses`, label: 'Licenses'},
    {to: `${base}/registrations`, label: 'Registrations'},
    {to: `${base}/trial`, label: 'Trial'},
    {to: `${base}/kofi`, label: 'Ko-fi'},
    {to: `${base}/mailing`, label: 'Mailing'},
    {to: `${base}/settings`, label: 'Settings'},
  ]
})

function switchGuild(event: Event) {
  const target = event.target as HTMLSelectElement
  const id = target.value
  if (!id) return
  const tail = route.path.replace(/^\/admin\/g\/[^/]+/, '')
  router.push(`/admin/g/${id}${tail || '/products'}`)
}
</script>

<template>
  <div class="mx-auto flex min-h-screen max-w-6xl gap-6 p-6">
    <aside class="hidden w-64 shrink-0 md:block">
      <header class="mb-6">
        <div class="text-xs uppercase tracking-wider opacity-60">
          Administering
        </div>
        <SelectInput
            v-if="guilds.length > 0"
            :model-value="currentGuildId ?? ''"
            class="mt-1"
            @change="switchGuild"
        >
          <option disabled value="">Select a guild…</option>
          <option v-for="g in guilds" :key="g.id" :value="g.id">
            {{ g.name }}{{ g.role === 'operator' ? ' (operator)' : '' }}
          </option>
        </SelectInput>
        <div v-else class="mt-1 text-sm opacity-70">
          No admin guilds available.
        </div>
      </header>
      <nav v-if="sections.length" class="space-y-1">
        <NuxtLink
            v-for="item in sections"
            :key="item.to"
            :to="item.to"
            active-class="bg-primary/10 text-primary font-medium"
            class="block rounded-theme px-3 py-2 hover:bg-primary/5"
        >
          {{ item.label }}
        </NuxtLink>
      </nav>
      <NuxtLink
          v-if="isOperator"
          to="/admin/instance/system"
          class="mt-4 block rounded-theme border border-border-light dark:border-border-dark px-3 py-2 text-xs uppercase tracking-wider opacity-70 hover:opacity-100"
      >
        Instance area →
      </NuxtLink>
    </aside>
    <main class="flex-1 min-w-0">
      <div v-if="currentGuild" class="mb-2 text-xs uppercase tracking-wider opacity-60">
        {{ currentGuild.name }}
      </div>
      <slot />
    </main>
  </div>
</template>
