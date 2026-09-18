/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
/**
 * One input with its label above it and, where there is one, a line underneath saying what it is for.
 *
 * <p>The label wraps the input rather than sitting beside it, so clicking the text focuses the field
 * without anything having to carry a matching id.
 */
defineProps<{
    label: string
    /** A line under the field, for the ones whose consequence is not obvious from the label. */
    help?: string
}>()
</script>

<template>
  <FieldLabel class="space-y-1">
    <span class="block">{{ label }}</span>
    <slot/>
    <MutedText v-if="help" size="sm" tag="span">{{ help }}</MutedText>
  </FieldLabel>
</template>
