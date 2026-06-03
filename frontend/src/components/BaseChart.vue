<script setup>
import { onBeforeUnmount, onMounted, shallowRef, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  option: {
    type: Object,
    required: true,
  },
  loading: {
    type: Boolean,
    default: false,
  },
})

const chartEl = shallowRef(null)
let chart = null
let resizeObserver = null

function renderChart() {
  if (!chartEl.value) return
  if (!chart) {
    chart = echarts.init(chartEl.value, null, { renderer: 'canvas' })
  }
  chart.setOption(props.option, true)
  if (props.loading) chart.showLoading('default')
  else chart.hideLoading()
}

onMounted(() => {
  renderChart()
  resizeObserver = new ResizeObserver(() => chart?.resize())
  resizeObserver.observe(chartEl.value)
})

watch(
  () => props.option,
  () => renderChart(),
  { deep: true },
)

watch(
  () => props.loading,
  (loading) => {
    if (!chart) return
    if (loading) chart.showLoading('default')
    else chart.hideLoading()
  },
)

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  chart?.dispose()
})
</script>

<template>
  <div ref="chartEl" class="base-chart"></div>
</template>

<style scoped>
.base-chart {
  width: 100%;
  height: 100%;
  min-height: inherit;
}
</style>
