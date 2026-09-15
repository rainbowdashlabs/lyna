/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import type {InstanceOperator} from '~/api/admin'
import {formatDateTime} from '~/util/format'

defineProps<{
    operators: InstanceOperator[]
}>()

defineEmits<{
    remove: [discordId: string]
}>()
</script>

<template>
  <DataTable>
    <template #head>
      <Th>Discord id</Th>
      <Th>Granted by</Th>
      <Th>Granted</Th>
      <Th align="right">Withdraw</Th>
    </template>
    <TRow v-for="operator in operators" :key="operator.discordId">
      <Td>
        <KeyBadge>{{ operator.discordId }}</KeyBadge>
        <SecondaryBadge v-if="operator.configured" class="ml-2">From the config</SecondaryBadge>
      </Td>
      <Td muted>{{ operator.addedBy ?? '—' }}</Td>
      <Td muted>{{ operator.addedAt ? formatDateTime(operator.addedAt) : '—' }}</Td>
      <Td align="right">
        <SecondaryButton
            v-if="!operator.configured"
            compact
            @click="$emit('remove', operator.discordId)"
        >
          Withdraw
        </SecondaryButton>
        <MutedText v-else size="sm">Edit the config</MutedText>
      </Td>
    </TRow>
  </DataTable>
</template>
