/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {InstanceOperator} from '~/api/admin'
import {formatDateTime} from '~/util/format'

const {t} = useI18n()

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
      <Th>{{ t('ui.operatorList.discordId') }}</Th>
      <Th>{{ t('ui.operatorList.grantedBy') }}</Th>
      <Th>{{ t('ui.operatorList.granted') }}</Th>
      <Th align="right">{{ t('ui.operatorList.withdraw') }}</Th>
    </template>
    <TRow v-for="operator in operators" :key="operator.discordId">
      <Td>
        <KeyBadge>{{ operator.discordId }}</KeyBadge>
        <SecondaryBadge v-if="operator.configured" class="ml-2">{{ t('ui.operatorList.fromTheConfig') }}</SecondaryBadge>
      </Td>
      <Td muted>{{ operator.addedBy ?? '—' }}</Td>
      <Td muted>{{ operator.addedAt ? formatDateTime(operator.addedAt) : '—' }}</Td>
      <Td align="right">
        <SecondaryButton
            v-if="!operator.configured"
            compact
            @click="$emit('remove', operator.discordId)"
        >
          {{ t('ui.operatorList.withdraw') }}
        </SecondaryButton>
        <MutedText v-else size="sm">{{ t('ui.operatorList.editTheConfig') }}</MutedText>
      </Td>
    </TRow>
  </DataTable>
</template>
