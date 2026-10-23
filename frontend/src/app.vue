/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, watch} from 'vue'
import {useTheme} from '~/composables/useTheme'
import {useSession} from '~/composables/useSession'
import {publicTheme} from '~/api/theme'

const {initFromLocalStorage, applyPolicy, adoptAccountChoice} = useTheme()
const {account} = useSession()

onMounted(async () => {
  initFromLocalStorage()
  applyPolicy(await publicTheme().catch(() => null))
  watch(() => account.value, signedIn => {
    if (signedIn) adoptAccountChoice(signedIn.theme)
  }, {immediate: true})
})
</script>

<template>
  <DemoBanner/>
  <NuxtLayout>
    <NuxtPage/>
  </NuxtLayout>
</template>
