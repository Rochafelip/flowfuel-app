# Roteiro de Debug — Android Auto

## Pré-requisitos (verificar uma vez)

- [ ] Modo desenvolvedor do Android ativo no celular
- [ ] USB debugging ativo (Configurações → Opções do desenvolvedor → Depuração USB)
- [ ] DHU instalado: `C:\Users\rocha\AppData\Local\Android\Sdk\extras\google\auto\desktop-head-unit.exe`
- [ ] Modo desenvolvedor do **Android Auto** ativo (10 taps no número da versão dentro do app)
- [ ] "Fontes desconhecidas" ativo nas opções de desenvolvedor do Android Auto
- [ ] "Servidor da unidade central" ativo nas opções de desenvolvedor do Android Auto

---

## Sequência (toda vez)

**1. Conecte o celular ao PC via USB e aceite o popup de autorização**

**2. Verifique que o celular está visível:**
```
"C:\Users\rocha\AppData\Local\Android\Sdk\platform-tools\adb.exe" devices
```
Deve aparecer `RQCTA05AS8Y  device`. Se aparecer `unauthorized`, desbloqueie a tela e aceite o popup.

**3. Forward da porta:**
```
"C:\Users\rocha\AppData\Local\Android\Sdk\platform-tools\adb.exe" forward tcp:5277 tcp:5277
```

**4. Inicie o logcat (deixe aberto numa aba separada):**
```
"C:\Users\rocha\AppData\Local\Android\Sdk\platform-tools\adb.exe" logcat *:W 2>&1 | findstr /i "flowfuel car auto CarApp"
```

**5. Inicie o DHU:**
```
"C:\Users\rocha\AppData\Local\Android\Sdk\extras\google\auto\desktop-head-unit.exe"
```

**6. No celular:** Abra o Android Auto → opções de desenvolvedor → toque em **"Iniciar servidor da unidade central"**

**7. O DHU deve conectar.** Se mostrar `[E]: failed`, repita o passo 3 e reinicie o Android Auto.

---

## Lendo o logcat

| Mensagem | Significado |
|---|---|
| `IllegalArgumentException: Min API level not declared` | Falta `<meta-data android:name="androidx.car.app.minCarApiLevel">` no Manifest |
| `Package DENIED; Uses for TEMPLATE not defined` | `automotive_app_desc.xml` incorreto ou ausente |
| `ALLOW_ALL_HOSTS_VALIDATOR` | Normal em desenvolvimento, não é erro |
| `CarApp.H.Tem: Error` | Falha no serviço do FlowFuel — leia a causa logo abaixo |
| `CAR.VALIDATOR: Package DENIED` | Manifest rejeitado pelo validador do Android Auto |

---

## Se o app não aparecer mesmo com DHU conectado

```
"C:\Users\rocha\AppData\Local\Android\Sdk\platform-tools\adb.exe" logcat *:E 2>&1 | findstr /i "CarApp flowfuel"
```

Isso filtra só erros — a causa raiz quase sempre aparece nas primeiras 10 linhas.

---

## Dispositivo

- Modelo: Samsung (serial `RQCTA05AS8Y`)
- Android Auto: versão com suporte a DHU v2.0
- DHU: Build 2022-03-30, Version 2.0-windows
