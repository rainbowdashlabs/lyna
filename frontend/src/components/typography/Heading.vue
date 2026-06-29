/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import PageHeader from './PageHeader.vue'
import SectionHeader from './SectionHeader.vue'
import SubHeader from './SubHeader.vue'

withDefaults(defineProps<{
    level?: 1 | 2 | 3
}>(), {
    level: 1,
})
</script>

<template>
    <PageHeader v-if="level === 1">
        <slot/>
    </PageHeader>
    <SectionHeader v-else-if="level === 2">
        <slot/>
    </SectionHeader>
    <SubHeader v-else>
        <slot/>
    </SubHeader>
</template>
