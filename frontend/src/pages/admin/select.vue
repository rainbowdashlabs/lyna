<script lang="ts" setup>
import {onMounted} from 'vue'
import {useAdminGuilds} from '~/composables/useAdminGuilds'

const {guilds, load} = useAdminGuilds()
onMounted(load)
</script>

<template>
  <main class="mx-auto max-w-2xl p-6">
    <PageHeader class="mb-4">
      Select a guild
    </PageHeader>
    <p class="mb-6 opacity-70">
      You administer more than one guild. Pick one to continue.
    </p>
    <ul class="space-y-2">
      <li v-for="g in guilds" :key="g.id">
        <NuxtLink
            :to="`/admin/g/${g.id}/products`"
            class="flex items-center justify-between rounded-theme border border-border-light dark:border-border-dark p-3 hover:bg-primary/5"
        >
          <span>
            <span class="font-medium">{{ g.name }}</span>
            <span class="ml-2 text-xs opacity-60">{{ g.role === 'operator' ? 'operator' : 'guild admin' }}</span>
          </span>
          <span aria-hidden="true">→</span>
        </NuxtLink>
      </li>
      <li v-if="guilds.length === 0" class="text-sm opacity-70">
        No admin access.
      </li>
    </ul>
  </main>
</template>
