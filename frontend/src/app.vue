<script lang="ts" setup>
import {onMounted} from 'vue'
import {useTheme} from '~/composables/useTheme'

const {initFromLocalStorage} = useTheme()

onMounted(() => {
  initFromLocalStorage()
})
</script>

<template>
  <DemoBanner/>
  <NuxtLayout>
    <NuxtPage/>
  </NuxtLayout>
</template>
