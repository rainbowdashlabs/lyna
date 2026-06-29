<script lang="ts" setup>
import {useRouter} from 'vue-router'
import {logout} from '~/api/account'
import {useSession} from '~/composables/useSession'

const router = useRouter()
const {clear, account} = useSession()

async function doLogout() {
  try {
    await logout()
  } catch {
    /* ignore — token will still be cleared client-side */
  }
  clear()
  await router.replace('/login')
}

const sidebar = [
  {to: '/account', label: 'Overview', exact: true},
  {to: '/account/security', label: 'Security'},
]
</script>

<template>
  <div class="mx-auto flex min-h-screen max-w-6xl gap-6 p-6">
    <aside class="hidden w-56 shrink-0 md:block">
      <header class="mb-6">
        <div class="text-xs uppercase tracking-wider opacity-60">
          Signed in as
        </div>
        <div class="truncate text-sm font-medium">
          {{ account?.email ?? account?.discordId ?? 'Anonymous' }}
        </div>
      </header>
      <nav class="space-y-1">
        <NuxtLink
            v-for="item in sidebar"
            :key="item.to"
            :to="item.to"
            :exact-active-class="item.exact ? 'bg-primary/10 text-primary font-medium' : ''"
            active-class="bg-primary/10 text-primary font-medium"
            class="block rounded-theme px-3 py-2 hover:bg-primary/5"
        >
          {{ item.label }}
        </NuxtLink>
      </nav>
      <button
          class="mt-6 w-full rounded-theme border border-border-light dark:border-border-dark px-3 py-2 text-left text-sm hover:bg-error/10 hover:text-error"
          @click="doLogout"
      >
        Log out
      </button>
    </aside>
    <main class="flex-1 min-w-0">
      <nav class="mb-4 flex gap-2 overflow-x-auto md:hidden">
        <NuxtLink
            v-for="item in sidebar"
            :key="item.to"
            :to="item.to"
            active-class="bg-primary/10 text-primary"
            class="rounded-theme border border-border-light dark:border-border-dark px-3 py-1.5 text-sm"
        >
          {{ item.label }}
        </NuxtLink>
        <button
            class="ml-auto rounded-theme border border-border-light dark:border-border-dark px-3 py-1.5 text-sm hover:text-error"
            @click="doLogout"
        >
          Log out
        </button>
      </nav>
      <slot />
    </main>
  </div>
</template>
