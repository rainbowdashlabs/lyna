/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'

/**
 * A dialog that asks for exactly one piece of text and confirms it.
 *
 * Confirming is blocked while the field is blank, so the caller never has to check for an empty
 * value. Every label is passed in, because these dialogs read very differently - "add a locale"
 * and "add a file" want their own wording, not a generic one.
 */
defineProps<{
  title: string
  placeholder: string
  confirmLabel: string
}>()

const show = defineModel<boolean>('show', {required: true})
const value = defineModel<string>('value', {required: true})

const emit = defineEmits<{
  confirm: []
}>()

const {t} = useI18n()
</script>

<template>
  <Modal v-model="show">
    <div class="space-y-4">
      <SubHeader>{{ title }}</SubHeader>
      <TextInput v-model="value" :placeholder="placeholder"/>
      <div class="flex justify-end gap-2">
        <SecondaryButton @click="show = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="!value.trim()" @click="emit('confirm')">
          {{ confirmLabel }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
