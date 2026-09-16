/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {setUsername} from '~/api/account'

const props = defineProps<{
    username: string | null
    /** False while Discord supplies the name, which is when this field is not the account's to set. */
    nameIsTheirs: boolean
}>()

const emit = defineEmits<{
    changed: []
}>()

const editing = ref(false)
const draft = ref('')
const busy = ref(false)
const message = ref<string | null>(null)
const isError = ref(false)

async function submit() {
  busy.value = true
  message.value = null
  isError.value = false
  try {
    const saved = await setUsername(draft.value)
    message.value = `You are now ${saved}. The digits keep you apart from anybody else who picked that name.`
    draft.value = ''
    editing.value = false
    emit('changed')
  } catch (e) {
    const error = e as {response?: {data?: string}}
    isError.value = true
    message.value = typeof error.response?.data === 'string'
        ? error.response.data
        : 'Could not set that name.'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section>
    <CardHeader>Username</CardHeader>
    <div class="space-y-2 text-sm">
      <div class="flex flex-wrap items-center gap-2">
        <span class="font-medium">{{ props.username ?? 'No name yet' }}</span>
        <SecondaryBadge v-if="!props.nameIsTheirs">From Discord</SecondaryBadge>
      </div>

      <MutedText tag="div">
        This is how you appear to somebody who shares a license with you. Your email address is never
        shown to them.
      </MutedText>

      <MutedText v-if="!props.nameIsTheirs" tag="div">
        Discord supplies this name. Unlink Discord to choose one yourself.
      </MutedText>

      <div v-else class="flex flex-wrap gap-2 pt-1">
        <SecondaryButton compact @click="editing = !editing">
          {{ editing ? 'Cancel' : props.username ? 'Change username' : 'Pick a username' }}
        </SecondaryButton>
      </div>

      <form v-if="props.nameIsTheirs && editing" class="space-y-2 pt-2" @submit.prevent="submit">
        <LabelledField
            help="Three to thirty-two characters: letters, digits, dots, dashes or underscores. Four digits are added so two people can share a name."
            label="Username"
        >
          <TextInput v-model="draft" autocomplete="username" required/>
        </LabelledField>
        <PrimaryButton :disabled="busy || !draft.trim()" compact @click="submit">
          {{ busy ? 'Saving…' : 'Save' }}
        </PrimaryButton>
      </form>

      <MutedText v-if="message" :class="isError ? 'text-error' : 'text-success'" class="block" size="sm">
        {{ message }}
      </MutedText>
    </div>
  </section>
</template>
