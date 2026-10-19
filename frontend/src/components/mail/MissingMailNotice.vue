/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'

/**
 * Names the products that have no mail. A receipt is matched to a product by its mail's name, so
 * these cannot be sold through one until they get a mail, even an empty one that sends the default.
 */
defineProps<{ products: string[], busy: boolean }>()

const emit = defineEmits<{ addMissing: [] }>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer>
    <p class="text-sm">
      {{ t('page.admin.g.guildId.mailing.withoutMail', {products: products.join(', ')}) }}
    </p>
    <PrimaryButton :disabled="busy" class="mt-2" compact @click="emit('addMissing')">
      {{ t('page.admin.g.guildId.mailing.addMissing') }}
    </PrimaryButton>
  </NeutralContainer>
</template>
