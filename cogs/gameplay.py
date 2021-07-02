import asyncio
import random

import discord
from discord.ext import commands
from discord_slash import cog_ext, ComponentContext
from discord_slash.utils.manage_commands import create_option
from discord_slash.utils.manage_components import create_actionrow, create_button
from discord_slash.model import ButtonStyle


class Gameplay(commands.Cog):
    def __init__(self, bot):
        self.bot = bot
        self.slash = bot.slash
        self.timer_task = None
        self.allow_enroll = False
        self.candidates = []

    async def timer(self, ctx, interval):
        await ctx.send(f'開始計時 {interval}秒')
        if interval >= 30:
            await asyncio.sleep(interval - 30)
            await ctx.send('剩下30秒')
            await asyncio.sleep(30)
            await ctx.send('計時器結束!')
        else:
            await asyncio.sleep(10)
            await ctx.send('計時器結束!')

    @cog_ext.cog_slash(name='timer', options=[
        create_option(name='interval', description='秒數', option_type=int, required=True)
    ], description='計時器')
    @commands.has_permissions(administrator=True)
    async def timer_cmd(self, ctx, interval: int):
        task = asyncio.create_task(self.timer(ctx, interval))
        self.timer_task = task
        await task

    @cog_ext.cog_slash(name='stop_timer', description='停止計時器')
    @commands.has_permissions(administrator=True)
    async def stop_timer(self, ctx):
        self.timer_task.cancel()
        await ctx.send('已停止')

    @cog_ext.cog_slash(name='expel_poll', description='針對活著的玩家舉行放逐投票')
    @commands.has_permissions(administrator=True)
    async def expel_poll(self, ctx):
        alive = []
        sorted_member = sorted(ctx.guild.members, key=lambda x: x.display_name)
        for member in sorted_member:
            member: discord.Member
            if any([i.name.startswith('玩家') for i in member.roles]):
                alive.append((f'{member.display_name} ({str(member)})', str(member.id)))
        bot = self.bot

        async def voter_check(int_ctx):
            if any([i.name.startswith('玩家') for i in int_ctx.author.roles]):
                return True
            else:
                await int_ctx.send('只有活人才能投票！', hidden=True)
                return False

        async def callback(winner):
            await ctx.send(f'投票結束，被放逐者：<@!{list(winner[0].keys())[0]}>')
            return
            if len(winner) > 1:
                await ctx.send('有平票！平票者將重新被票選！')
                re_elected = []
                for w in winner:
                    w = ctx.guild.get_member(int(list(w.keys())[0]))
                    re_elected.append((f'{w.display_name} ({str(w)})', str(w.id)))
                await bot.voting.generate_new_poll(ctx, '放逐投票', re_elected, 1, 20, False, False, callback, voter_check)
            else:
                await ctx.send(f'投票結束，被放逐者：<@!{list(winner[0].keys())[0]}>')

        await self.bot.voting.generate_new_poll(ctx, '放逐投票', alive, 1, 20, False, False, callback, voter_check)

    @cog_ext.cog_slash(name='police_enroll', description='發動參選警長的投票')
    @commands.has_permissions(administrator=True)
    async def _police_enroll(self, ctx):
        self.allow_enroll = True
        embed = discord.Embed(title='欲參選警長者請按下按鈕', description='時間只有15秒！請加快手速！')
        msg = await ctx.send(embed=embed, components=[
            create_actionrow(create_button(style=ButtonStyle.green, label='參選警長', custom_id='police_enroll'))])

        @self.slash.component_callback(use_callback_name=False, messages=msg)
        async def police_enroll(btn_ctx: ComponentContext):
            if self.allow_enroll:
                if btn_ctx.author not in self.candidates:
                    self.candidates.append(btn_ctx.author)
                    await btn_ctx.send('參選成功！', hidden=True)
                else:
                    self.candidates.remove(btn_ctx.author)
                    await btn_ctx.send('退選成功！', hidden=True)
            else:
                if btn_ctx.author in self.candidates:
                    self.candidates.remove(btn_ctx.author)
                    await btn_ctx.send('退選成功！', hidden=True)
                    await btn_ctx.send(btn_ctx.author.mention + '已退選！')

        await asyncio.sleep(10)
        await ctx.send('剩下5秒!')
        await asyncio.sleep(5)
        self.allow_enroll = False
        if len(self.candidates) == 0:
            await ctx.send('無人參選警長！')
        else:
            res = sorted(x.display_name for x in self.candidates)
            await ctx.send(f'參選的有: {"，".join(res)}')
            order = random.choice(['上', '下'])
            speaker = random.choice(res)
            await ctx.send(f'發言順序：{speaker}{order}\n\n注意：已參選者可以隨時再按一次來退選，但不可參選')

    @cog_ext.cog_slash(name='police_poll', description='上警投票')
    @commands.has_permissions(administrator=True)
    async def police_poll(self, ctx):
        if len(self.candidates) == 0:
            await ctx.send('無人參選警長或尚未啟動參選警長選單！')
            return
        candidates = []
        sorted_member = sorted(self.candidates, key=lambda x: x.display_name)
        for member in sorted_member:
            member: discord.Member
            candidates.append((f'{member.display_name} ({str(member)})', str(member.id)))
        bot = self.bot

        async def voter_check(int_ctx):
            if any([i.name.startswith('玩家') for i in int_ctx.author.roles]) and int_ctx.author not in self.candidates:
                return True
            else:
                await int_ctx.send('只有活人和非參選人才能投票！', hidden=True)
                return False

        async def callback(winner):
            await ctx.send(f'投票結束，當選警長者：<@!{list(winner[0].keys())[0]}>')
            return
            if len(winner) > 1:
                await ctx.send('有平票！平票者將重新被票選！')
                re_elected = []
                for w in winner:
                    w = ctx.guild.get_member(list(w.keys())[0])
                    re_elected.append((f'{w.display_name} ({str(w)})', str(w.id)))
                await bot.voting.generate_new_poll(ctx, '警長投票', re_elected, 1, 20, False, False, callback, voter_check)
            else:
                await ctx.send(f'投票結束，當選警長者：<@!{list(winner[0].keys())[0]}>')

        await self.bot.voting.generate_new_poll(ctx, '警長投票', candidates, 1, 20, False, False, callback, voter_check)

    @cog_ext.cog_slash(name='order', description='隨機發言順序')
    async def order(self, ctx):
        alives = []
        sorted_member = sorted(ctx.guild.members, key=lambda x: x.display_name)
        for member in sorted_member:
            member: discord.Member
            if any([i.name.startswith('玩家') for i in member.roles]):
                alives.append(member)
        person = random.choice(alives)
        order = random.choice(['上', '下'])
        await ctx.send(f'順序：{person.display_name} {order}')

    @cog_ext.cog_slash(name='dead', options=[
        create_option(name='member', description='要使變成旁觀者/死人的人', option_type=6, required=True)
    ], description='讓人變成旁觀者或死人')
    @commands.has_permissions(administrator=True)
    async def dead(self, ctx, member: discord.Member):
        dead_role = discord.utils.get(ctx.guild.roles, name='旁觀者 / 死人')
        for r in [i.name.startswith('玩家') for i in member.roles]:
            await member.remove_roles(r)
        await member.add_roles(dead_role)


def setup(bot):
    bot.add_cog(Gameplay(bot))
