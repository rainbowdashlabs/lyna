/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {fragmentsOf} from '~/util/debugLog'

/**
 * A line of text with whatever the search matched marked. A match is waiting on the reader, so it
 * takes the pink wash.
 */
const props = defineProps<{
    text: string
    pattern: RegExp | null
}>()

const fragments = computed(() => fragmentsOf(props.text, props.pattern))
</script>

<template>
  <span><template v-for="(fragment, index) in fragments" :key="index"><mark v-if="fragment.match" class="bg-primary/35 text-(--text)">{{ fragment.text }}</mark><template v-else>{{ fragment.text }}</template></template></span>
</template>
