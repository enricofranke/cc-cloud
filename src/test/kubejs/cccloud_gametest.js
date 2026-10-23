// Dev only: copied into run/kubejs/server_scripts by -PintegrationMods=kubejs (see build.gradle).
// The kubejs_bindings GameTest sends the alert "kubejs-ping"; this script answers through the CCCloud binding by
// publishing a section "pong" with a toggle and records dashboard actions on it, so the test sees the full round trip:
// core event -> CCCloudEvents.alert -> JS -> CCCloud.section(...).publish() -> dashboard action -> onAction -> JS.

CCCloudEvents.alert(event => {
  if (event.text != 'kubejs-ping') return
  const id = event.mainframeId
  CCCloud.section(id, 'pong')
    .title('Pong')
    .gauge('level', 'Level', { max: 100, unit: '%' })
    .toggle('lamp', 'Lamp')
    .select('mode', 'Mode', { options: ['Eco', 'Boost'] })
    .onAction(ctx => {
      if (ctx.widget == 'mode' && ctx.value == 'Boost') ctx.reject('no_boost')
      else CCCloud.set(id, 'pong', 'level', ctx.value ? 100 : 0)
    })
    .publish()
  CCCloud.set(id, 'pong', 'level', 42)
})

CCCloudEvents.action(event => {
  if (event.section == 'Pong' && event.widget == 'lamp') CCCloud.alert(event.mainframeId, 'pong', 'lamp by ' + event.playerName, 'info')
})
