<script lang="ts" setup>
import {onMounted} from 'vue'
import {useRouter} from 'vue-router'
import {useAdminGuilds} from '~/composables/useAdminGuilds'

const router = useRouter()
const {guilds, load} = useAdminGuilds()

onMounted(async () => {
  await load()
  if (guilds.value.length === 0) {
    await router.replace('/account')
    return
  }
  if (guilds.value.length === 1) {
    await router.replace(`/admin/g/${guilds.value[0]!.id}/products`)
    return
  }
  await router.replace('/admin/select')
})
</script>

<template>
  <div class="flex min-h-screen items-center justify-center">
    <p class="text-sm opacity-70">
      Resolving admin context…
    </p>
  </div>
</template>
