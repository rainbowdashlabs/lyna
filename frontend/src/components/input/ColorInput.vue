/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
const model = defineModel<string>({ default: '#6b7280' })
</script>

<template>
    <label class="block w-8 h-8 rounded cursor-pointer shrink-0 border border-(--border) overflow-hidden" :style="{ backgroundColor: model }">
        <input type="color" :value="model" class="opacity-0 w-0 h-0" @input="model = ($event.target as HTMLInputElement).value" />
    </label>
</template>
