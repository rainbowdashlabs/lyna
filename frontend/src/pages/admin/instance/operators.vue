<script lang="ts" setup>
import {onMounted, ref} from 'vue'
import {addOperator, type InstanceOperator, listOperators, removeOperator} from '~/api/admin'

definePageMeta({layout: 'admin'})

const operators = ref<InstanceOperator[]>([])
const loading = ref(true)
const errorMessage = ref<string | null>(null)
const newId = ref('')
const busy = ref(false)
const actionError = ref<string | null>(null)
const removing = ref<string | null>(null)

async function refresh() {
  operators.value = await listOperators()
}

onMounted(async () => {
  try {
    await refresh()
  } catch (e) {
    const error = e as {response?: {status?: number}}
    errorMessage.value = error.response?.status === 404
        ? 'Operator access required.'
        : 'Failed to load the operators.'
  } finally {
    loading.value = false
  }
})

/**
 * Reports what the backend refused rather than a generic failure: every refusal here is one an
 * operator can do something about - a malformed id, an id the configuration already names, or the
 * last one standing.
 */
function report(e: unknown, fallback: string) {
  const error = e as {response?: {data?: string}}
  actionError.value = typeof error.response?.data === 'string' ? error.response.data : fallback
}

async function doAdd() {
  busy.value = true
  actionError.value = null
  try {
    await addOperator(newId.value)
    newId.value = ''
    await refresh()
  } catch (e) {
    report(e, 'Could not grant the instance to that id.')
  } finally {
    busy.value = false
  }
}

async function doRemove() {
  const discordId = removing.value
  if (!discordId) return
  removing.value = null
  actionError.value = null
  try {
    await removeOperator(discordId)
    await refresh()
  } catch (e) {
    report(e, 'Could not withdraw the instance from that id.')
  }
}
</script>

<template>
  <div>
    <PageHeader class="mb-4">
      Operators
    </PageHeader>
    <MutedText class="mb-4 block" size="sm">
      An operator reaches every guild's admin area and this one. Ids named in the configuration file
      cannot be withdrawn here &mdash; they are the way back in if a grant goes wrong.
    </MutedText>

    <AsyncSection :error="errorMessage ?? undefined" :loading="loading">
      <div class="space-y-4">
        <OperatorList :operators="operators" @remove="removing = $event"/>

        <NeutralContainer>
          <CardHeader>Grant the instance</CardHeader>
          <div class="flex flex-wrap items-end gap-2">
            <LabelledField class="flex-1" label="Discord user id">
              <TextInput v-model="newId" inputmode="numeric" placeholder="e.g. 128014201982911361"/>
            </LabelledField>
            <PrimaryButton :disabled="busy || !newId.trim()" @click="doAdd">
              {{ busy ? 'Granting…' : 'Grant' }}
            </PrimaryButton>
          </div>
          <MutedText v-if="actionError" class="mt-2 block text-error" size="sm">{{ actionError }}</MutedText>
        </NeutralContainer>
      </div>
    </AsyncSection>

    <ConfirmDeleteModal
        :message="`Withdraw the instance from ${removing}? They lose every guild's admin area and this one.`"
        :model-value="removing !== null"
        @confirm="doRemove"
        @update:model-value="removing = $event ? removing : null"
    />
  </div>
</template>
