/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
/**
 * Muted, italic single-line hint used for "no data" / "not configured" / "no longer available"
 * placeholders. Centralised so the styling stays consistent and the lint rule for repeated
 * `<p class="text-sm text-(--text-muted) italic">` blocks doesn't kick in.
 */
</script>

<template>
    <p class="text-sm text-(--text-muted) italic"><slot/></p>
</template>
