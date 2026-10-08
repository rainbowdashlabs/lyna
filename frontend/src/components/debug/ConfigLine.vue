/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {type TokenKind, yamlTokens} from '~/util/debugLog'

/**
 * A line of a configuration file, coloured as YAML. While a search runs it shows the matches
 * instead, since the two marks over each other read as neither.
 */
const props = defineProps<{
    number: number
    text: string
    pattern: RegExp | null
}>()

const TOKEN_CLASS: Record<TokenKind, string> = {
    key: 'text-secondary-badge dark:text-secondary',
    string: 'text-success',
    number: 'text-info',
    literal: 'text-primary-badge dark:text-primary',
    comment: 'text-(--text-muted) italic',
    plain: '',
}

const tokens = computed(() => yamlTokens(props.text))
</script>

<template>
  <div class="grid grid-cols-[3.5rem_1fr] text-xs leading-5">
    <span class="font-data pr-3 text-right text-[11px] text-(--text-muted) select-none">{{ number }}</span>
    <LogLineText v-if="pattern" :pattern="pattern" :text="text" class="font-data whitespace-pre"/>
    <span v-else class="font-data whitespace-pre"><span v-for="(token, index) in tokens" :key="index" :class="TOKEN_CLASS[token.kind]">{{ token.text }}</span></span>
  </div>
</template>
